// Native host: official HDC authentication; private Unix socket; no shell execution as App UID.
#include <napi/native_api.h>
#include "server.h"
#include "wire.h"
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <poll.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <thread>
#include <stdexcept>

namespace {
std::mutex initMutex, commandMutex, socketMutex;
std::condition_variable initCv;
bool attempted=false, ready=false, failed=false;
std::string socketPath;
int activeSocket=-1;
std::atomic<unsigned long> cancelGeneration{0};
using namespace AgentWire;

std::string StringArg(napi_env env,napi_value v) {
    napi_valuetype type; napi_typeof(env,v,&type);
    if(type!=napi_string) throw std::runtime_error("Expected string argument");
    size_t n=0; napi_get_value_string_utf8(env,v,nullptr,0,&n);
    if(n>32768) throw std::runtime_error("Argument too large");
    std::vector<char> data(n+1); napi_get_value_string_utf8(env,v,data.data(),data.size(),&n);
    std::string s(data.data(),n);
    if(s.find('\0')!=std::string::npos) throw std::runtime_error("NUL in argument");
    return s;
}
void Start(const std::string& dir) {
    std::unique_lock<std::mutex> lock(initMutex);
    if(!attempted) {
        if(dir.empty()||dir[0]!='/'||dir.size()>80)throw std::runtime_error("Invalid private HDC directory");
        attempted=true;socketPath=dir+"/host.sock";
        std::thread([dir]{
            try {
                Hdc::Base::SetAgentDir(dir);Hdc::UDS_PATH=socketPath;
                Hdc::Base::SetLogLevel(Hdc::LOG_OFF);
                auto* server=new Hdc::HdcServer(true);
                bool ok=server->Initial("");
                {std::lock_guard<std::mutex> g(initMutex);ready=ok;failed=!ok;}initCv.notify_all();
                if(ok)server->WorkerPendding();
                {std::lock_guard<std::mutex> g(initMutex);ready=false;failed=true;}initCv.notify_all();
                // Lifetime is process-wide; HDC does not support safe thread-level restart.
            } catch(...) {{std::lock_guard<std::mutex> g(initMutex);failed=true;}initCv.notify_all();}
        }).detach();
    }
    if(!initCv.wait_for(lock,std::chrono::seconds(10),[]{return ready||failed;})||!ready)throw std::runtime_error("本机调试服务启动失败，请重启助手后重试");
}
std::string Execute(const std::string& command,const std::string& target,int timeout,unsigned long generation) {
    std::lock_guard<std::mutex> serial(commandMutex);
    if(generation!=cancelGeneration.load())throw std::runtime_error("CANCELLED");
    {std::lock_guard<std::mutex> g(initMutex);if(!ready)throw std::runtime_error("本机调试服务尚未启动");}
    if(command.rfind("shell ",0)!=0 && command.rfind("tconn ",0)!=0 && command!="list targets")throw std::runtime_error("Unsupported HDC command");
    if(target.size()>31)throw std::runtime_error("Target too long");
    int fd=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);if(fd<0)throw std::runtime_error("Cannot create HDC socket");
    {std::lock_guard<std::mutex> g(socketMutex);activeSocket=fd;}
    auto cleanup=[fd]{std::lock_guard<std::mutex> g(socketMutex);activeSocket=-1;close(fd);};
    try {
        sockaddr_un addr{};addr.sun_family=AF_UNIX;
        if(socketPath.size()>=sizeof(addr.sun_path))throw std::runtime_error("Socket path too long");
        memcpy(addr.sun_path,socketPath.c_str(),socketPath.size()+1);
        if(connect(fd,reinterpret_cast<sockaddr*>(&addr),sizeof(addr)))throw std::runtime_error("Cannot connect private HDC host");
        if(generation!=cancelGeneration.load())throw std::runtime_error("CANCELLED");
        auto deadline=Clock::now()+std::chrono::milliseconds(timeout);
        std::string hello;
        ReadFrame(fd,hello,deadline);
        std::string reply=Handshake(hello,target);
        WriteFrame(fd,reply,deadline);WriteFrame(fd,command+std::string(1,'\0'),deadline);
        std::string output,frame;
        while(ReadFrame(fd,frame,deadline,true)) {
            if(output.size()+frame.size()>MAX_OUTPUT)throw std::runtime_error("HDC output exceeds limit");
            output+=frame;
        }
        if(generation!=cancelGeneration.load())throw std::runtime_error("CANCELLED");
        cleanup();return output;
    } catch(...) {cleanup();throw;}
}
struct Work {napi_async_work work{};napi_deferred deferred{};std::string a,b,result,error;int timeout=20000;bool start=false;unsigned long generation=0;};
void Run(napi_env,void* ptr) {auto* w=static_cast<Work*>(ptr);try{if(w->start){Start(w->a);w->result="ready";}else w->result=Execute(w->a,w->b,w->timeout,w->generation);}catch(const std::exception& e){w->error=e.what();}}
void Done(napi_env env,napi_status status,void* ptr) {
    auto* w=static_cast<Work*>(ptr);napi_value value;
    if(status!=napi_ok && w->error.empty())w->error="Native work cancelled";
    napi_create_string_utf8(env,w->error.empty()?w->result.data():w->error.data(),w->error.empty()?w->result.size():w->error.size(),&value);
    if(w->error.empty())napi_resolve_deferred(env,w->deferred,value);else{napi_value err;napi_create_error(env,nullptr,value,&err);napi_reject_deferred(env,w->deferred,err);}
    napi_delete_async_work(env,w->work);delete w;
}
napi_value Queue(napi_env env,napi_callback_info info,bool start) {
    auto* w=new Work();w->start=start;w->generation=cancelGeneration.load();
    try {
        napi_value args[3];size_t argc=3;napi_get_cb_info(env,info,&argc,args,nullptr,nullptr);
        if(argc<(start?1:3))throw std::runtime_error("Missing native arguments");
        w->a=StringArg(env,args[0]);
        if(!start){w->b=StringArg(env,args[1]);if(napi_get_value_int32(env,args[2],&w->timeout)!=napi_ok || w->timeout<100 || w->timeout>60000)throw std::runtime_error("Invalid timeout");}
        napi_value promise,name;napi_create_promise(env,&w->deferred,&promise);napi_create_string_utf8(env,"agentHdc",NAPI_AUTO_LENGTH,&name);
        if(napi_create_async_work(env,nullptr,name,Run,Done,w,&w->work)!=napi_ok)throw std::runtime_error("Cannot create native work");
        if(napi_queue_async_work(env,w->work)!=napi_ok){napi_delete_async_work(env,w->work);throw std::runtime_error("Cannot queue native work");}
        return promise;
    } catch(const std::exception& e){delete w;napi_throw_error(env,nullptr,e.what());return nullptr;}
}
napi_value StartJs(napi_env e,napi_callback_info i){return Queue(e,i,true);}
napi_value CommandJs(napi_env e,napi_callback_info i){return Queue(e,i,false);}
napi_value CancelJs(napi_env e,napi_callback_info){cancelGeneration++;std::lock_guard<std::mutex> g(socketMutex);if(activeSocket>=0)shutdown(activeSocket,SHUT_RDWR);napi_value v;napi_get_undefined(e,&v);return v;}
napi_value Init(napi_env env,napi_value exports){napi_property_descriptor p[]={{"start",nullptr,StartJs,nullptr,nullptr,nullptr,napi_default,nullptr},{"command",nullptr,CommandJs,nullptr,nullptr,nullptr,napi_default,nullptr},{"cancel",nullptr,CancelJs,nullptr,nullptr,nullptr,napi_default,nullptr}};napi_define_properties(env,exports,3,p);return exports;}
napi_module module={1,0,nullptr,Init,"agent_hdc",nullptr,{0}};
}
extern "C" __attribute__((constructor)) void RegisterAgentHdc(){napi_module_register(&module);}

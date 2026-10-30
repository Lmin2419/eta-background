#include "../entry/src/main/cpp/wire.h"
#include <unistd.h>
#include <cassert>
#include <functional>
#include <iostream>
#include <thread>

using namespace AgentWire;
struct Pair {
    int fd[2];
    Pair(){assert(socketpair(AF_UNIX,SOCK_STREAM,0,fd)==0);}
    ~Pair(){for(int f:fd)if(f>=0)close(f);}
    void closeWriter(){close(fd[1]);fd[1]=-1;}
};
auto deadline(){return Clock::now()+std::chrono::seconds(2);}
void rejects(const std::function<void()>& f){bool caught=false;try{f();}catch(const std::runtime_error&){caught=true;}assert(caught);}
int main(){
    {
        Pair p;std::string data="你好\nresult";
        uint32_t n=htonl(data.size());std::string bytes(reinterpret_cast<char*>(&n),4);bytes+=data;
        std::thread writer([&]{for(char c:bytes){assert(send(p.fd[1],&c,1,MSG_NOSIGNAL)==1);std::this_thread::yield();}shutdown(p.fd[1],SHUT_WR);});
        std::string result;assert(ReadFrame(p.fd[0],result,deadline()));assert(result==data);
        assert(!ReadFrame(p.fd[0],result,deadline(),true));writer.join();
    }
    {
        Pair p;std::string binary("shell id\0",9);WriteFrame(p.fd[1],binary,deadline());
        std::string result;assert(ReadFrame(p.fd[0],result,deadline()));assert(result==binary);assert(result.back()=='\0');
    }
    {
        Pair p;uint32_t n=htonl(MAX_OUTPUT+1);send(p.fd[1],&n,4,0);std::string result;
        rejects([&]{ReadFrame(p.fd[0],result,deadline());});
    }
    {
        Pair p;char partial[2]={0,0};send(p.fd[1],partial,2,0);p.closeWriter();std::string result;
        rejects([&]{ReadFrame(p.fd[0],result,deadline(),true);});
    }
    {
        Pair p;uint32_t n=htonl(10);send(p.fd[1],&n,4,0);send(p.fd[1],"abc",3,0);p.closeWriter();std::string result;
        rejects([&]{ReadFrame(p.fd[0],result,deadline(),true);});
    }
    {
        Pair p;std::string result;auto start=Clock::now();
        rejects([&]{ReadFrame(p.fd[0],result,start+std::chrono::milliseconds(100));});
        assert(Clock::now()-start<std::chrono::seconds(1));
    }
    {
        Pair p;std::string result;shutdown(p.fd[0],SHUT_RDWR);
        assert(!ReadFrame(p.fd[0],result,deadline(),true));
    }
    {
        std::string hello="OHOS HDC";hello.resize(44,0);hello[10]='K';hello[11]='H';
        const auto reply=Handshake(hello,"127.0.0.1:5555");assert(reply.size()==44);
        assert(reply.substr(0,12)==hello.substr(0,12));assert(reply.substr(12,14)==std::string("127.0.0.1:5555\0",14));
        rejects([&]{Handshake("invalid","");});rejects([&]{Handshake(hello,std::string(32,'x'));});
    }
    std::cout<<"8 native framing checks passed\n";
}

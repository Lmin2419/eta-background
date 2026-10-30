#pragma once
#include <arpa/inet.h>
#include <sys/socket.h>
#include <poll.h>
#include <cerrno>
#include <chrono>
#include <cstdint>
#include <stdexcept>
#include <string>

namespace AgentWire {
constexpr size_t MAX_OUTPUT=16*1024*1024;
using Clock=std::chrono::steady_clock;
inline void Wait(int fd,short events,Clock::time_point deadline) {
    for(;;) {
        auto ms=std::chrono::duration_cast<std::chrono::milliseconds>(deadline-Clock::now()).count();
        if(ms<=0) throw std::runtime_error("HDC_TIMEOUT: operation outcome unknown; do not replay");
        pollfd p{fd,events,0}; int rc=poll(&p,1,static_cast<int>(ms));
        if(rc>0) return;
        if(rc<0 && errno==EINTR) continue;
        throw std::runtime_error("HDC_TIMEOUT: operation outcome unknown; do not replay");
    }
}
inline bool Read(int fd,char* out,size_t n,Clock::time_point deadline,bool eofAllowed=false) {
    size_t at=0;
    while(at<n) {
        Wait(fd,POLLIN,deadline);
        auto got=recv(fd,out+at,n-at,0);
        if(got==0 && at==0 && eofAllowed) return false;
        if(got<=0) {if(got<0&&errno==EINTR)continue;throw std::runtime_error("HDC_DISCONNECTED: incomplete response");}
        at+=static_cast<size_t>(got);
    }
    return true;
}
inline bool ReadFrame(int fd,std::string& data,Clock::time_point deadline,bool eofAllowed=false) {
    uint32_t len;
    if(!Read(fd,reinterpret_cast<char*>(&len),4,deadline,eofAllowed))return false;
    len=ntohl(len);
    if(len>MAX_OUTPUT)throw std::runtime_error("HDC frame exceeds limit");
    data.resize(len);return Read(fd,data.data(),len,deadline);
}
inline void WriteFrame(int fd,const std::string& data,Clock::time_point deadline) {
    uint32_t len=htonl(static_cast<uint32_t>(data.size()));
    std::string packet(reinterpret_cast<char*>(&len),4);packet+=data;
    size_t at=0;
    while(at<packet.size()) {
        Wait(fd,POLLOUT,deadline);auto n=send(fd,packet.data()+at,packet.size()-at,MSG_NOSIGNAL);
        if(n<=0){if(n<0&&errno==EINTR)continue;throw std::runtime_error("HDC send failed");}at+=n;
    }
}
inline std::string Handshake(const std::string& hello,const std::string& target) {
    if(hello.size()!=44 || hello.compare(0,8,"OHOS HDC")!=0)throw std::runtime_error("Invalid HDC handshake");
    if(target.size()>31 || target.find('\0')!=std::string::npos)throw std::runtime_error("Invalid HDC target");
    std::string reply=hello.substr(0,12);reply.resize(44,0);reply.replace(12,target.size(),target);return reply;
}
}

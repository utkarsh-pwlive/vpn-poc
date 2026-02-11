- InstagramBlockVpnService: This creates the VPN tunnel, where all traffic flows.
- DnsProcessor.run()
  - This is a loop that constantly:read packet, analyze packet, write response
  - extractDomain(): pulls instagram.com from packet
  - buildBlockedDnsResponse(): creates NXDOMAIN packet and sends back.

 ```
The VPN intercepts DNS traffic and inspects domain names.
When a request matches instagram.com, it returns an NXDOMAIN
response instead of forwarding to a real DNS server.
Without an IP address, the app cannot establish a connection,
effectively blocking access.
```

### Low level design

```
App → OS → VPN Tunnel
        |
        v
   VpnRunner (thread)
        |
        v
   read(packet)
        |
        v
   DnsProcessor.process()
        |
   +----+----+
   |         |
Blocked?   Allowed?
   |         |
NXDOMAIN   forwardToDNS
   |         |
   +----+----+
        |
        v
   write(response)
        |
        v
App receives DNS result
```

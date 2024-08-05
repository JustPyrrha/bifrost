# bifrost
validated server transfers

## how it works
when you join a server it sets a jwt as a client cookie with claims of the players uuid and the servers ip and port.\
when transferring the new server with fake a client query to the original server and grab the original servers public RSA key via a hijacked ping response packet.\
this is very hacky and only a proof of concept, please for the love of gods, dont use this in production.

not implemented:
- a way for servers to identify if theyre part of the correct bifrost network

note: make sure to set `server-ip` and `accepts-transfers=true` otherwise it doesnt work
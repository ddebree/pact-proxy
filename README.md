# pact-proxy

[![Build Status](https://travis-ci.org/ddebree/pact-proxy.svg?branch=master)](https://travis-ci.org/ddebree/pact-proxy)

A proxy that generates pact files. All you need to do is point to a remote server and then access the services via the proxy server's url.

## Usage

java -jar pact-proxy.jar --port=5555 --remote="https://api.github.com"
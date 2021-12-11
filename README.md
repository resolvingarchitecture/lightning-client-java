[![Build Status](https://travis-ci.com/resolvingarchitecture/lightning-client-java.svg?branch=master)](https://travis-ci.com/resolvingarchitecture/lightning-client-java)

# Resolving Architecture - Lightning Client - Java
Lightning Client as a Service

Provides an API as a Service for a local Lightning node via its RPC API.

## Setup


## Authors / Developers

* objectorange (Brian Taylor) - [GitHub](https://github.com/objectorange) | [LinkedIn](https://www.linkedin.com/in/decentralizationarchitect/) | brian@resolvingarchitecture.io PGP: 2FA3 9B12 DA50 BD7C E43C 3031 A15D FABB 2579 77DC

## Design
LightningService is designed to work with the RA Service Bus. It sends and expects to receive messages using it.
It currently depends on the ra.http.HTTPService running as a client only (for local Lightning node RPC).

Lightning JSON-RPC interface is used for all communications to the local Lightning node.

Future versions of this component will provide auto notifications of payments channels that would be affected by a prune when registered with this service.

## Configuration


## Implementation
RPC operations implemented using the following API documentation: https://api.lightning.community/#lnd-rest-api-reference

## Release Notes

### 1.0
* Send and Receive Bitcoins via the local Lightning node verified.

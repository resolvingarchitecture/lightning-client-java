package ra.lnd.uses;

import ra.lnd.rpc.RPCRequest;
import ra.lnd.rpc.RPCResponse;
import ra.common.JSONSerializable;

public interface UseRequest extends JSONSerializable {
    Boolean additionalRequests();
    RPCRequest nextRequest();
    void handleResponse(RPCRequest request, RPCResponse response);
}

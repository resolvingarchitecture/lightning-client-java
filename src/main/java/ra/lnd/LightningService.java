package ra.lnd;

import ra.lnd.rpc.RPCRequest;
import ra.lnd.rpc.RPCResponse;
import ra.lnd.uses.UseRequest;
import ra.common.Envelope;
import ra.common.messaging.MessageProducer;
import ra.common.route.Route;
import ra.common.service.BaseService;
import ra.common.service.ServiceStatus;
import ra.common.service.ServiceStatusObserver;
import ra.common.Config;
import ra.common.JSONParser;
import ra.common.SystemSettings;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.logging.Logger;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * Service for providing access to the Lightning network
 */
public class LightningService extends BaseService {

    private static final Logger LOG = Logger.getLogger(LightningService.class.getName());

    public static final String REMOTE_HOST = "ra.btc.remotehost";

    public static final String LOCAL_RPC_HOST = "http://127.0.0.1:";

    public static final Integer MAIN_NET_PORT = 8332;
    public static final Integer TEST_NET_PORT = 18332;
    public static final Integer REG_TEST_PORT = 18443;

    public static final String AUTHN = "Basic cmE6MTIzNA==";

    // RPC Requests
    public static final String OPERATION_RPC_REQUEST = "LND_RPC_REQUEST";
    public static final String OPERATION_RPC_RESPONSE = "LND_RPC_RESPONSE";

    public static final String OPERATION_USE_REQUEST = "USE_REQUEST";

    private final NodeConfig nodeConfig = new NodeConfig();
    public static URL rpcUrl;
    private final List<LightningPeer> peers = new ArrayList<>();

    // holds RPCRequests for simple correlation of RPCResponses
    private final Map<String, RPCRequest> clientRPCRequestHold = new HashMap<>();
    private final Map<String, UseRequest> clientUseRequestHold = new HashMap<>();
    // holds internal RPCRequests that are required to fulfill external RPCRequests, e.g. need to ensure a wallet is loaded prior to checking its balance
    private final Map<String, RPCRequest> internalRequestHold = new HashMap<>();

    private byte mode = 0; // 0 = local, 1 = remote personal, 2 = random remote non-personal
    private String currentWalletName = "";

    public LightningService() {
    }

    public LightningService(MessageProducer producer, ServiceStatusObserver observer) {
        super(producer, observer);
    }

    @Override
    public void handleDocument(Envelope e) {
        Route route = e.getRoute();
        String operation = route.getOperation();
        switch(operation) {
            case OPERATION_RPC_REQUEST: {
                RPCRequest request = extractRPCRequest(e);
                RPCRequest requestToForward = request;
                if(isNull(request)) return;
                String corrId = UUID.randomUUID().toString();
                e.addNVP(LightningService.class.getName()+".corrId", corrId);
                clientRPCRequestHold.put(corrId, request);

                try {
                    forwardRequest(e, requestToForward);
                } catch (MalformedURLException malformedURLException) {
                    LOG.warning(malformedURLException.getLocalizedMessage());
                }
                break;
            }
            case OPERATION_RPC_RESPONSE: {
                Object obj = e.getContent();
                String responseStr = new String((byte[]) obj);
                LOG.info("LND RPC Response: " + responseStr);
                RPCResponse response = new RPCResponse();
                response.fromJSON(responseStr);
                String corrId = (String) e.getValue(LightningService.class.getName() + ".corrId");
                RPCRequest clientRequest = clientRPCRequestHold.get(corrId);
                RPCRequest internalRequest = internalRequestHold.get(corrId);
                if(nonNull(response.error)) {
                    handleError(clientRequest, response);
                }
                e.addNVP(RPCCommand.RESPONSE, response.toMap());
                clientRPCRequestHold.remove(corrId);
                break;
            }
            case OPERATION_USE_REQUEST: {
                UseRequest useRequest = extractUseRequest(e);
                if(isNull(useRequest)) return;
                String corrId = UUID.randomUUID().toString();
                e.addNVP(LightningService.class.getName()+".corrId", corrId);
                clientUseRequestHold.put(corrId, useRequest);

            }
            default:
                deadLetter(e); // Operation not supported
        }
    }

    private boolean sendInternalRequest(RPCRequest request) throws MalformedURLException {
        Envelope e = Envelope.documentFactory();
        String corrId = UUID.randomUUID().toString();
        e.addNVP(LightningService.class.getName()+".corrId", corrId);
        internalRequestHold.put(corrId, request);
        return forwardRequest(e, request);
    }

    private boolean forwardRequest(Envelope e, RPCRequest request) throws MalformedURLException {
        String json = request.toJSON();
        e.addNVP(RPCCommand.NAME, json);
        e.setURL(new URL(LightningService.rpcUrl, request.path));
        e.setAction(Envelope.Action.POST);
        e.setHeader(Envelope.HEADER_AUTHORIZATION, LightningService.AUTHN);
        e.setHeader(Envelope.HEADER_CONTENT_TYPE, Envelope.HEADER_CONTENT_TYPE_JSON);
        LOG.info("Sending to LND Node: "+json);
        e.addContent(json);
        e.addRoute(LightningService.class.getName(), OPERATION_RPC_RESPONSE);
        e.addExternalRoute("ra.http.HTTPService", "SEND");
        return send(e);
    }

    private void handleError(RPCRequest request, RPCResponse response) {
        LOG.warning(response.error.code+":"+response.error.message);

    }

    private RPCRequest extractRPCRequest(Envelope e) {
        RPCRequest request = null;
        Object reqObj = e.getValue(RPCCommand.NAME);
        if(isNull(reqObj)) {
            e.addErrorMessage(RPCCommand.NAME + " value required.");
        } else if(reqObj instanceof RPCRequest) {
            request = (RPCRequest) reqObj;
        } else if(reqObj instanceof Map) {
            try {
                request = RPCRequest.inflate((Map<String, Object>) e.getValue(RPCCommand.NAME));
            } catch (Exception ex) {
                LOG.warning("Unable to inflate RPCRequest from map so can not make Lightning RPC call; ignoring: " + ex.getLocalizedMessage());
                e.addErrorMessage("Unable to inflate RPCRequest from map so can not make Lightning RPC call; ignoring: " + ex.getLocalizedMessage());
            }
        } else if(reqObj instanceof String) {
            try {
                Map<String,Object> tempReqM = (Map<String,Object>)JSONParser.parse((String)reqObj);
                request = RPCRequest.inflate(tempReqM);
            } catch (Exception ex) {
                LOG.warning("Unable to inflate RPCRequest from string so can not make Lightning RPC call; ignoring: " + ex.getLocalizedMessage());
                e.addErrorMessage("Unable to inflate RPCRequest from string so can not make Lightning RPC call; ignoring: " + ex.getLocalizedMessage());
            }
        } else {
            e.addErrorMessage("Must provide an RPCRequest, Map of RPCRequest, or JSON of RPCRequest.");
        }
        return request;
    }

    private UseRequest extractUseRequest(Envelope e) {

        return null;
    }

    private void updateInfo(RPCRequest request, RPCResponse response) {
        if(nonNull(response.error)) {
            LOG.warning(response.error.toString());
        } else {
            switch (request.method) {
                // TODO: Refactor out switch by passing fully qualified class name and using reflection to create instance then map

            }
        }
    }

    @Override
    public boolean start(Properties p) {
        LOG.info("Starting...");
        updateStatus(ServiceStatus.STARTING);
        if(!super.start(p))
            return false;
        LOG.info("Loading properties...");
        try {
            config = Config.loadAll(p, "ra-lnd.config");
//            String modeParam = config.getProperty("ra.btc.mode");
//            if(modeParam!=null) {
//                mode = Byte.parseByte(modeParam);
//            }
            String env = config.getProperty("ra.env");
            if("test".equalsIgnoreCase(env) || "qa".equalsIgnoreCase(env))
                rpcUrl = new URL("http://localhost:"+TEST_NET_PORT);
            else if("prod".equalsIgnoreCase(env)) {
                rpcUrl = new URL("http://localhost:"+MAIN_NET_PORT);
            } else {
                rpcUrl = new URL("http://localhost:"+REG_TEST_PORT);
            }
            String lndCfgDir;
            if(nonNull(config.getProperty("ra.lnd.directory"))) {
                lndCfgDir = config.getProperty("ra.lnd.directory");
            } else {
                lndCfgDir = SystemSettings.getUserHomeDir().getAbsolutePath() + "/snap/lightning/common/.lightning/";
            }
            LOG.info(lndCfgDir);
            LOG.info("NodeConfig loaded: "+nodeConfig.load(lndCfgDir));
        } catch (Exception e) {
            LOG.severe(e.getLocalizedMessage());
            return false;
        }

        updateStatus(ServiceStatus.RUNNING);
        LOG.info("Started.");
        return true;
    }

    @Override
    public boolean shutdown() {
        LOG.info("Shutting down...");
        updateStatus(ServiceStatus.SHUTTING_DOWN);


        updateStatus(ServiceStatus.SHUTDOWN);
        LOG.info("Shutdown.");
        return true;
    }

    @Override
    public boolean gracefulShutdown() {
        LOG.info("Gracefully shutting down...");
        updateStatus(ServiceStatus.GRACEFULLY_SHUTTING_DOWN);


        updateStatus(ServiceStatus.GRACEFULLY_SHUTDOWN);
        LOG.info("Gracefully shutdown.");
        return true;
    }

//    public static void main(String[] args) {
//        BitcoinService service = new BitcoinService();
//        service.setProducer(new MessageProducer() {
//            @Override
//            public boolean send(Envelope envelope) {
//                return true;
//            }
//
//            @Override
//            public boolean send(Envelope envelope, Client client) {
//                return true;
//            }
//
//            @Override
//            public boolean deadLetter(Envelope envelope) {
//                return true;
//            }
//        });
//        Properties props = new Properties();
//        for(String arg : args) {
//            String[] nvp = arg.split("=");
//            props.put(nvp[0],nvp[1]);
//        }
//        if(service.start(props)) {
//            while(service.getServiceStatus() != ServiceStatus.SHUTDOWN) {
//                Wait.aSec(1);
//            }
//        } else {
//            System.exit(-1);
//        }
//    }

}

package ra.lnd;

import ra.common.JSONParser;
import ra.common.JSONPretty;
import ra.common.JSONSerializable;
import ra.common.currency.crypto.BTC;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import static java.util.Objects.nonNull;

public class LNDWallet implements JSONSerializable {

    private String name;
    private Integer version;
    private BigInteger balance;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public BigInteger getBalance() {
        return balance;
    }

    public void setBalance(BigInteger balance) {
        this.balance = balance;
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String,Object> m = new HashMap<>();
        m.put("walletname", name);
        m.put("walletversion", version);
        m.put("balance", balance);
        return m;
    }

    @Override
    public void fromMap(Map<String, Object> m) {
        if(nonNull(m.get("walletname"))) name = (String)m.get("walletname");
        if(nonNull(m.get("walletversion"))) version = (Integer)m.get("walletversion");
        Object balObj = m.get("balance");
        if(nonNull(balObj)) {
            if(balObj instanceof BigInteger)
                balance = (BigInteger)m.get("balance");
            else if(balObj instanceof Double)
                balance = new BTC((Double)m.get("balance")).value();
        }
    }

    @Override
    public String toJSON() {
        return JSONPretty.toPretty(JSONParser.toString(toMap()), 4);
    }

    @Override
    public void fromJSON(String json) {
        fromMap((Map<String,Object>)JSONParser.parse(json));
    }
}

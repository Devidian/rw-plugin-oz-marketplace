package de.omegazirkel.risingworld.marketplace;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.risingworld.api.Plugin;

public class WalletBridge {
    private final Plugin owner;

    public WalletBridge(Plugin owner) {
        this.owner = owner;
    }

    public boolean isAvailable() {
        try {
            return owner.getPluginByName("OZ - Wallet") != null
                    && Class.forName("de.omegazirkel.risingworld.Wallet") != null;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    public WalletCallResult withdraw(int playerDbId, long value, String reason, String currencyIdentifier,
            String pluginIdentifier) {
        if (currencyIdentifier == null || currencyIdentifier.isBlank()) {
            return call("withdrawDefault",
                    new Class<?>[] { int.class, long.class, String.class, String.class },
                    new Object[] { playerDbId, value, reason, pluginIdentifier });
        }
        return call("withdraw",
                new Class<?>[] { int.class, long.class, String.class, String.class, String.class },
                new Object[] { playerDbId, value, reason, currencyIdentifier, pluginIdentifier });
    }

    public WalletCallResult deposit(int playerDbId, long value, String reason, String currencyIdentifier,
            String pluginIdentifier) {
        if (currencyIdentifier == null || currencyIdentifier.isBlank()) {
            return call("depositDefault",
                    new Class<?>[] { int.class, long.class, String.class, String.class },
                    new Object[] { playerDbId, value, reason, pluginIdentifier });
        }
        return call("deposit",
                new Class<?>[] { int.class, long.class, String.class, String.class, String.class },
                new Object[] { playerDbId, value, reason, currencyIdentifier, pluginIdentifier });
    }

    private WalletCallResult call(String methodName, Class<?>[] paramTypes, Object[] args) {
        Plugin walletPlugin = owner.getPluginByName("OZ - Wallet");
        if (walletPlugin == null) {
            return new WalletCallResult(false, "OZ - Wallet is not installed or not loaded.");
        }

        try {
            Method method = walletPlugin.getClass().getMethod(methodName, paramTypes);
            Object result = method.invoke(walletPlugin, args);
            Object success = field(result, "success");
            Object message = field(result, "message");
            return new WalletCallResult(Boolean.TRUE.equals(success), message instanceof String ? (String) message : "");
        } catch (ReflectiveOperationException ex) {
            return new WalletCallResult(false, "Wallet call failed: " + ex.getMessage());
        }
    }

    private Object field(Object result, String fieldName) {
        if (result == null) {
            return null;
        }
        try {
            Field field = result.getClass().getField(fieldName);
            return field.get(result);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    public record WalletCallResult(boolean success, String message) {
    }
}

package com.foodfight.room;

import com.foodfight.net.Protocol.ErrorMsg;

import java.util.ArrayList;
import java.util.List;

/**
 * A pretend browser for room tests. Instead of sending messages over a
 * socket, it keeps them in a list so the test can look at them.
 */
class FakeLink implements PlayerLink {

    final List<Object> sent = new ArrayList<>();
    boolean backedUp = false;
    int detachCount = 0;

    @Override
    public void send(Object message) {
        sent.add(message);
    }

    @Override
    public boolean isBackedUp() {
        return backedUp;
    }

    @Override
    public void detach(Room room) {
        detachCount++;
    }

    /** Every message of one type, in order. */
    <T> List<T> all(Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Object message : sent) {
            if (type.isInstance(message)) {
                result.add(type.cast(message));
            }
        }
        return result;
    }

    /** The newest message of one type, or null. */
    <T> T last(Class<T> type) {
        List<T> all = all(type);
        return all.isEmpty() ? null : all.get(all.size() - 1);
    }

    /** The code of the newest error, or null if there was none. */
    String lastErrorCode() {
        ErrorMsg error = last(ErrorMsg.class);
        return error == null ? null : error.code();
    }
}

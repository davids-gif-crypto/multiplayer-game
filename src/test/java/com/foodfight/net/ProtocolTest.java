package com.foodfight.net;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.foodfight.net.Protocol.Input;
import com.foodfight.net.Protocol.Join;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for reading and writing messages. */
class ProtocolTest {

    @Test
    void readsAJoin() throws Exception {
        Join join = assertInstanceOf(Join.class,
                Protocol.parse("{\"t\":\"join\",\"room\":\"abcd\",\"name\":\"Sam\",\"extra\":1}"));
        assertEquals("abcd", join.room());
        assertEquals("Sam", join.name());
    }

    @Test
    void readsAnInputAndOnlyTrustsRealBooleans() throws Exception {
        Input input = assertInstanceOf(Input.class,
                Protocol.parse("{\"t\":\"input\",\"seq\":7,\"left\":true,\"right\":\"true\",\"jump\":1}"));
        assertEquals(7, input.seq());
        assertTrue(input.left());
        assertFalse(input.right()); // text "true" is not a boolean
        assertFalse(input.jump());  // the number 1 is not a boolean
        assertFalse(input.attack()); // missing means false
    }

    @Test
    void unknownOrBrokenMessagesGiveNull() throws Exception {
        assertNull(Protocol.parse("{\"t\":\"explode\"}"));
        assertNull(Protocol.parse("{\"room\":\"ABCD\"}"));
        assertNull(Protocol.parse("[1,2,3]"));
        assertNull(Protocol.parse("{\"t\":\"closed\"}")); // browsers cannot fake a disconnect
    }

    @Test
    void wrongTypesBecomeNull() throws Exception {
        Join join = assertInstanceOf(Join.class, Protocol.parse("{\"t\":\"join\",\"room\":5,\"name\":{}}"));
        assertNull(join.room());
        assertNull(join.name());
    }

    @Test
    void notJsonThrows() {
        assertThrows(JsonProcessingException.class, () -> Protocol.parse("hello"));
    }

    @Test
    void serverMessagesIncludeTheirType() {
        assertEquals("{\"t\":\"pong\"}", Protocol.toJson(new Protocol.Pong()));
        assertEquals("{\"t\":\"countdown\",\"n\":3}", Protocol.toJson(new Protocol.Countdown(3)));
    }
}

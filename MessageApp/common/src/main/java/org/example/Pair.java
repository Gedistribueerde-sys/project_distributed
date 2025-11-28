package org.example;

import java.io.Serializable;

public record Pair(String value, String tag) implements Serializable {

    @Override
    public String toString() {
        return "Pair[" + value + ", " + tag + "]";
    }
}

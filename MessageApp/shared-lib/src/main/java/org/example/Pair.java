package org.example;

import java.io.Serializable;

public record Pair(byte[] value, String tag) implements Serializable {
}

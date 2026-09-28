package com.example.sqlide.misc;

@FunctionalInterface
public interface ThrowingRunnable {
    Object run() throws Exception;
}

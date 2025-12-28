package com.pharmalocator;

/**
 * Launcher class that does not extend Application.
 * This is required for JavaFX applications packaged as fat JARs.
 */
public class Launcher {
    public static void main(String[] args) {
        MainApp.main(args);
    }
}


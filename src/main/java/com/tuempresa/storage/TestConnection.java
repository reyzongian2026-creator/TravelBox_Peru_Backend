package com.tuempresa.storage;

import java.sql.DriverManager;
import java.sql.Connection;

/**
 * Utility to verify JDBC connectivity.
 * Reads credentials from environment variables — never hard-code secrets.
 *
 * Usage:
 *   DB_URL=jdbc:postgresql://host:5432/db?sslmode=require \
 *   DB_USER=myuser DB_PASSWORD=mypass \
 *   java com.tuempresa.storage.TestConnection
 */
public class TestConnection {
    public static void main(String[] args) {
        String url = System.getenv("DB_URL");
        String user = System.getenv("DB_USER");
        String password = System.getenv("DB_PASSWORD");

        if (url == null || user == null || password == null) {
            System.err.println("ERROR: Set DB_URL, DB_USER, and DB_PASSWORD environment variables.");
            System.exit(1);
        }

        System.out.println("Testing JDBC connection...");
        System.out.println("URL: " + url);
        System.out.println("User: " + user.substring(0, Math.min(3, user.length())) + "***");

        try {
            Class.forName("org.postgresql.Driver");
            Connection conn = DriverManager.getConnection(url, user, password);
            System.out.println("SUCCESS! Connected!");
            conn.close();
        } catch (Exception e) {
            System.out.println("FAILED: " + e.getMessage());
        }
    }
}

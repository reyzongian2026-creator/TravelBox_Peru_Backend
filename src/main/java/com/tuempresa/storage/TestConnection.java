package com.tuempresa.storage;

import java.sql.DriverManager;
import java.sql.Connection;

public class TestConnection {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://travelbox-postgres.postgres.database.azure.com:5432/postgres?sslmode=require";
        String user = "travelboxadmin";
        String password = "TbX2024Azure";
        
        System.out.println("Testing JDBC connection...");
        System.out.println("URL: " + url);
        System.out.println("User: " + user);
        
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
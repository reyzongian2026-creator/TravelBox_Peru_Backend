import java.sql.DriverManager;
import java.sql.Connection;
import java.properties;

public class TestDbConnection {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://travelbox-postgres.postgres.database.azure.com:5432/postgres?sslmode=require";
        String user = "travelboxadmin";
        String password = "G14nfr4nc0030525@";

        System.out.println("URL: " + url);
        System.out.println("User: " + user);
        System.out.println("Password: " + password);

        try {
            Class.forName("org.postgresql.Driver");
            Connection conn = DriverManager.getConnection(url, user, password);
            System.out.println("SUCCESS! Connected to database");
            conn.close();
        } catch (Exception e) {
            System.out.println("FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
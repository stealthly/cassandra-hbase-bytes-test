package org.kiji.cassandra;

import com.datastax.driver.core.*;
import org.apache.hadoop.hbase.util.Bytes;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Test C* byte[] wrapper.
 */
public class App {

  public static final String KEYSPACE_NAME = "demo";
  public static final String TABLE_NAME = "users";

  public static void main(String[] args) {

    // Connect to the cluster and open a session
    Cluster cluster = Cluster.builder().addContactPoint("172.16.7.2").build();
    Session session = cluster.connect();

    // Create the keyspace and table
    session.execute("CREATE KEYSPACE IF NOT EXISTS " + KEYSPACE_NAME +
        " WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 };");
    session.execute("CREATE TABLE IF NOT EXISTS " + KEYSPACE_NAME + "." + TABLE_NAME + " " +
        "( name text PRIMARY KEY, name_as_bytes blob );");

    //----------------------------------------------------------------------------------------------
    // Insert name as String and blob, read back, and very that everything looks good!
    String name = "Ms. Foo Bar";

    // Emulate what most of Kiji would do, and turn the String into byte[].

    byte[] nameToWriteAsByteArray = Bytes.toBytes(name);
    //byte[] nameToWriteAsByteArray = new byte[10];

    // Now do what we need to do for a C* blob: Turn the byte[] into a ByteBuffer
    ByteBuffer nameToWriteAsByteBuffer = ByteBuffer.wrap(nameToWriteAsByteArray);

    // Some quick sanity checks here
    assert(name.equals(Bytes.toString(nameToWriteAsByteArray)));
    assert(name.equals(Bytes.toString(Bytes.toBytes(nameToWriteAsByteBuffer))));



    // Now actually insert the name as a string and as a blob into the table!
    String queryText = "INSERT INTO " + KEYSPACE_NAME + "." + TABLE_NAME + "(name, name_as_bytes) " +
        "VALUES (?,?);";
    PreparedStatement preparedStatement = session.prepare(queryText);
    session.execute(preparedStatement.bind(name, nameToWriteAsByteBuffer));

    // Now read the value back out
    queryText = "SELECT * FROM " + KEYSPACE_NAME + "." + TABLE_NAME + " WHERE name=?";
    preparedStatement = session.prepare(queryText);
    ResultSet resultSet = session.execute(preparedStatement.bind(name));

    // Now convert the blob into a string
    List<Row> rows = resultSet.all();
    assert(rows.size() == 1);
    Row row = rows.get(0);

    String nameReadAsString = row.getString("name");
    if (!nameReadAsString.equals(name)) {
      System.err.println("Oopsies!  Expected to get name " + name + " from table, but got " + nameReadAsString + " instead!");
    }


    // Read the blob out as a ByteBuffer and convert to String through byte[]
    ByteBuffer nameReadAsBlobByteBuffer = row.getBytes("name_as_bytes");
    System.out.println("Raw byte buffer: " + nameReadAsBlobByteBuffer);

    // This is what we would pass to the normal Kiji code (that is designed to work with HBase):
    byte[] nameReadAsBlobByteArray = new byte[nameReadAsBlobByteBuffer.remaining()];
    nameReadAsBlobByteBuffer.get(nameReadAsBlobByteArray);
    
    // Now convert back to a string and check
    String nameReadAsBlobString = Bytes.toString(nameReadAsBlobByteArray);

    if (!nameReadAsBlobString.equals(name)) {
      System.err.println("Problem reading back blob!");
      System.err.println("Expected to read back >:" + name +":<");
      System.err.println("Got instead >:" + nameReadAsBlobString + ":<");
      System.err.println("Client -> table byte[] = " + nameToWriteAsByteArray);
      System.err.println("Table -> client byte[] = " + nameReadAsBlobByteArray);
    } else {
        System.out.println("Blob matches fine!");
    }

    cluster.shutdown();
  }
}

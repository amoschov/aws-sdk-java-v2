/*
 * Copyright 2010-2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.enhanced.dynamodb;

import java.time.Instant;
import java.util.Collections;
import org.junit.BeforeClass;
import org.junit.Test;
import software.amazon.awssdk.enhanced.dynamodb.model.ResponseItem;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;
import software.amazon.awssdk.testutils.Waiter;

public class EnhancedClientIntegrationTest {
    private static final String TABLE = "books";
    private static final DynamoDbClient dynamo = DynamoDbClient.create();

    @BeforeClass
    public static void setup() {
        try {
            dynamo.createTable(r -> r.tableName(TABLE)
                                     .keySchema(k -> k.attributeName("isbn").keyType(KeyType.HASH))
                                     .attributeDefinitions(a -> a.attributeName("isbn").attributeType(ScalarAttributeType.S))
                                     .provisionedThroughput(t -> t.readCapacityUnits(5L)
                                                                  .writeCapacityUnits(5L)));
        } catch (ResourceInUseException e) {
            // Table already exists. Awesome.
        }

        System.out.println("Waiting for table to be active...");

        Waiter.run(() -> dynamo.describeTable(r -> r.tableName(TABLE)))
              .until(r -> r.table().tableStatus().equals(TableStatus.ACTIVE))
              .orFail();
    }

    @Test
    public void getCanReadTheResultOfPut() throws InterruptedException {
        try (DynamoDbEnhancedClient client = DynamoDbEnhancedClient.create()) {
            Table books = client.table("books");

            System.out.println("Putting item...");

            books.putItem(i -> i.putAttribute("isbn", "0-330-25864-8")
                                .putAttribute("title", "The Hitchhiker's Guide to the Galaxy")
                                .putAttribute("publicationDate", p -> p.putAttribute("UK", Instant.parse("1979-10-12T00:00:00Z"))
                                                                       .putAttribute("US", Instant.parse("1980-01-01T00:00:00Z")))
                                .putAttribute("authors", Collections.singletonList("Douglas Adams")));

            Thread.sleep(5_000);

            System.out.println("Getting item...");

            ResponseItem book = books.getItem(key -> key.putAttribute("isbn", "0-330-25864-8"));

            System.out.println("ISBN: " + book.attribute("isbn").asString() + "\n" +
                               "Title: " + book.attribute("title").asString() + "\n" +
                               "Publication Dates: " + book.attribute("publicationDate").asMap(String.class, Instant.class) + "\n" +
                               "Authors: " + book.attribute("authors").asList(String.class));
        }
    }

    @Test
    public void getCanReadTheResultOfPutAsync() throws InterruptedException {
        try (DynamoDbEnhancedAsyncClient client = DynamoDbEnhancedAsyncClient.create()) {
            AsyncTable books = client.table("books");

            System.out.println("Putting item...");

            books.putItem(i -> i.putAttribute("isbn", "0-330-25864-8")
                                .putAttribute("title", "The Hitchhiker's Guide to the Galaxy")
                                .putAttribute("publicationDate", p -> p.putAttribute("UK", Instant.parse("1979-10-12T00:00:00Z"))
                                                                       .putAttribute("US", Instant.parse("1980-01-01T00:00:00Z")))
                                .putAttribute("authors", Collections.singletonList("Douglas Adams")))
                 .join();

            Thread.sleep(5_000);

            System.out.println("Getting item...");

            ResponseItem book = books.getItem(r -> r.putAttribute("isbn", "0-330-25864-8"))
                                     .join();

            System.out.println("ISBN: " + book.attribute("isbn").asString() + "\n" +
                               "Title: " + book.attribute("title").asString() + "\n" +
                               "Publication Dates: " + book.attribute("publicationDate").asMap(String.class, Instant.class) + "\n" +
                               "Authors: " + book.attribute("authors").asList(String.class));
        }
    }
}

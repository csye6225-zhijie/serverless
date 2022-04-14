package handler;

import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.PutItemOutcome;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;

import utils.SendEmailSMTP;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class SendVerificationEmail implements RequestHandler<SNSEvent, Object> {

    //    private DynamoDbClient dynamoDbClient = DynamoDbClient.builder().region(Region.US_EAST_1).build();
    private Regions REGION = Regions.US_EAST_1;
    private DynamoDB dynamoDB;
    private final String tableName = "useremail_status";

    public Object handleRequest(SNSEvent snsEvent, Context context) {
        //TODO:here we send the email with verification token
        context.getLogger().log("SendVerificationEmail invoked");
        context.getLogger().log("Dynamo Client Init Start");
        this.initDynamoDbClient();
        context.getLogger().log("Dynamo Client Init End");
        String timeStamp = new SimpleDateFormat("yyyy-HH-dd_HH:mm:ss").format(Calendar.getInstance().getTime());
        context.getLogger().log("Invocation started: " + timeStamp);
        context.getLogger().log("Request is null: " + (snsEvent == null));
        context.getLogger().log("Number of Records: " + (snsEvent.getRecords().size()));

        Map<String, SNSEvent.MessageAttribute> records = snsEvent.getRecords().get(0).getSNS().getMessageAttributes();
        if (records == null) {
            context.getLogger().log("No Message Attributes Found");
            return null;
        }

        SNSEvent.MessageAttribute userEmailMessageAttribute = records.get("UserEmail");
        if (userEmailMessageAttribute == null) {
            context.getLogger().log("No User Email Found");
            return null;
        }
        String userEmail = userEmailMessageAttribute.getValue();
        if (userEmail == null) {
            context.getLogger().log("No Valid User Email Found");
            return null;
        }
        SNSEvent.MessageAttribute tokenMessageAttribute = records.get("Token");
        if (tokenMessageAttribute == null) {
            context.getLogger().log("No Token Found");
            return null;
        }
        String token = tokenMessageAttribute.getValue();
        if (token == null) {
            context.getLogger().log("No Valid Token Found");
            return null;
        }
//        Sample verificaiton link
//        http://prod.domain.tld/v1/verifyUserEmail?email=user@example.com&token=sometoken
        String verificationLink = "https://prod.domain.tldA/v1/verifyUserEmail?email=" + Base64.getEncoder().withoutPadding().encodeToString(userEmail.getBytes(StandardCharsets.UTF_8)) + "&token=" + token;
        context.getLogger().log("Verification Link: " + verificationLink);
        String recipient = userEmail;
        context.getLogger().log("recipient email " + recipient);

        // do dynamo check
//        context.getLogger().log("Start build DynamoUtil");
//        DynamoUtil dynamoUtil = new DynamoUtil();
//        context.getLogger().log("Build DynamoUtil Success");
//        Map<String, AttributeValue> returnedItem = getDynamoDBItem("useremail", recipient);
//        context.getLogger().log("Dynamo returnedItem is " + returnedItem);
        Table table = dynamoDB.getTable(tableName);
        context.getLogger().log("Dynamo Table is " + table);
        try {
            Item sendRecord = table.getItem("useremail", recipient);
            context.getLogger().log("Item SendRecord is " + sendRecord);
//            String sendStatus = sendRecord.getString("status");
//            context.getLogger().log("Send Status is " + sendStatus);
            if (sendRecord == null) {
                Item newSendRecord = new Item().withPrimaryKey("useremail",recipient)
                        .withString("status","send");
                try{
                    PutItemOutcome outcome = table.putItem(newSendRecord);
                    context.getLogger().log("PutItemOutcome is " + outcome);
                } catch (Exception e) {
                    context.getLogger().log("Put Item Exception " + e.getMessage());
                }
                try {
                    SendEmailSMTP.send(recipient,verificationLink);
                } catch (Exception e) {
                    context.getLogger().log("Send Email Exception " + e.getMessage());
                }
            } else {
                return null;
            }
        } catch (Exception e) {
            context.getLogger().log("No Send Record Found!");
        }


        timeStamp = new SimpleDateFormat("yyyy-HH-dd_HH:mm:ss").format(Calendar.getInstance().getTime());
        context.getLogger().log("Invocation complete: " + timeStamp);
        return null;
    }

    private void initDynamoDbClient() {
        AmazonDynamoDBClient dynamoDBClient = new AmazonDynamoDBClient();
        dynamoDBClient.setRegion(Region.getRegion(REGION));
        this.dynamoDB = new DynamoDB(dynamoDBClient);
    }

}
package com.company.myapp.activeMQ;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;

/**
 * @deprecated 
 * ActiveMQ Producer 객체
 * @author 정영훈
 *
 */
public class Producer {
	String myQueue = "agent";
	
	public void send(String text) throws JMSException {
		// connection factory 생성
		ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("tcp://localhost:61616");
		
		// connection 생성
		Connection connection = factory.createConnection();
		connection.start();
		
		// session 생성
		Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

		// destination 생성 (topic or queue)
		Destination destination = session.createQueue(myQueue);
		
		// MessageProducer 생성 
		MessageProducer producer = session.createProducer(destination);
		
		TextMessage message = session.createTextMessage(text);
		
		producer.send(message);
		session.close();
		connection.close();
	}
}

package com.company.myapp.service;

import java.io.IOException;
import java.util.Properties;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import lombok.extern.log4j.Log4j2;

/**
 * 메일 전송 서비스
 * @author 정영훈, 김나영
 *
 */
@Log4j2
public class MailService {
	private String[] adminEmail; // 관리자 이메일
	private String systemEmail; // 시스템 이메일
	private String systemPassword; // 시스템 비밀번호
	
	private Session session;
	
	/**
	 * SMTP 설정 및 시스템 계정 등록
	 * @throws IOException 
	 */
	public MailService() {
		// 관리자, 시스템 이메일 세팅
		try {
			Properties agentProps = new Properties();
			agentProps.load(MailService.class.getResourceAsStream("/agent.properties"));
			adminEmail= agentProps.getProperty("admin.email").split(",");
			systemEmail= agentProps.getProperty("system.email");
			systemPassword= agentProps.getProperty("system.password");
			
			session = Session.getDefaultInstance(agentProps, new Authenticator() {
				@Override
				protected PasswordAuthentication getPasswordAuthentication() {
					return new PasswordAuthentication(systemEmail, systemPassword);
				}
			});
		}catch(Exception e) {
			e.printStackTrace();
		}
	
	}
	
	
	/**
	 * 메일 전송
	 * @param json 메일에 담을 내용
	 */
	public void sendMail(String title, String content) {
		Message message = new MimeMessage(session);
		
		try {
			message.setFrom(new InternetAddress(systemEmail,"BATCH_AGENT_SYSTEM","utf-8")); // FROM 등록
			
			InternetAddress[] addr = new InternetAddress[adminEmail.length];
			for(int i=0; i<adminEmail.length; i++) {
				addr[i] = new InternetAddress(adminEmail[i]);
			}
			
			message.addRecipients(Message.RecipientType.TO, addr); // TO 등록
			message.setSubject(title); //제목
			message.setContent(content, "text/html; charset=utf-8"); //내용
			log.info("메일 전송");
			Transport.send(message); //보내기
		} catch (Exception e) {
			e.printStackTrace();
		} 
	}
}

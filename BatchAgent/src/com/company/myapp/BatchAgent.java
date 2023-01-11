package com.company.myapp;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.mail.Authenticator;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;

import org.json.JSONArray;
import org.json.JSONObject;

import com.company.myapp.dto.JsonDto;
import com.company.myapp.service.MailService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.log4j.Log4j2;

/**
 * 배치 관리 서버와 통신해 배치 프로그램 실행 및 결과 반환
 * @author 정영훈, 김나영
 *
 */
@Log4j2
public class BatchAgent {
	
	
	String rootPath; // 배치파일 경로
	String managementIp; // 관리서버 아이피
	int managementPort; // 관리서버 포트
	
	ExecutorService threadPool;
	ServerSocket serverSocket;
	
	JSONArray pathArray = new JSONArray();
	Properties properties = new Properties();
	MailService mailService = new MailService();
	
	/**
	 * 설정파일 정보 로드
	 * 스레드풀, 서버소켓 생성 및 연결 수락 
	 * @throws IOException
	 */	
	public void start() throws IOException {
		log.info("[서버] 시작");
		
		// 설정파일에 있는 정보 로드
		properties.load(BatchAgent.class.getResourceAsStream("/agent.properties"));
		rootPath = properties.getProperty("agent.batch.path");
		managementPort = Integer.parseInt(properties.get("management.server.port").toString());
		managementIp = properties.getProperty("management.server.ip");
		
		int port = Integer.parseInt(properties.get("agent.server.port").toString());
		int threadNum = Integer.parseInt(properties.get("threadNum").toString());

		// 스레드풀 생성
		threadPool = Executors.newFixedThreadPool(threadNum);
		
		// 서버소켓 생성 및 포트 바인딩
		serverSocket = new ServerSocket(port);
		
		// 작업 스레드 생성
		threadPool.execute(new Runnable() {
			
			@Override
			public void run() {
				if(!serverSocket.isClosed()) {
					try {
						while(true) {
							Socket socket = serverSocket.accept();
							receiveMessage(socket);
						}
					} catch (Exception e) {
						log.info("[응답 에러]" + e.getMessage());
					}
				}
			}
		});
	}
	
	/**
	 * 스레드풀 종료, 서버소켓 닫음
	 * @throws IOException
	 */	
	public void shutdown() throws IOException {
		log.info("[서버] 종료");		
		threadPool.shutdown();
		serverSocket.close();
	}
	
	/**
	 * 배치 관리 서버 연결 및 Json 데이터 전송
	 * -- 연결 실패시 관리자에게 메일 전송
	 * @param json 보낼 JSON 객체
	 * @throws IOException 
	 */	
	public void sendMessage(JsonDto sendData) {
		try {
			
			if(sendData == null) throw new RuntimeException();
			
			// 소켓 생성 및 배치 관리 서버 연결 요청
			Socket socket = new Socket(managementIp, managementPort);
			DataOutputStream dos = new DataOutputStream(socket.getOutputStream());

			log.info("[배치 관리 서버 연결] "+ managementIp + ":" + managementPort);
			
			ObjectMapper mapper = new ObjectMapper();
			String message = mapper.writeValueAsString(sendData);
			JSONObject json = new JSONObject();
			json.put("cmd", "log");
			json.put("message", message);
			String sendDataStr = json.toString();
			
			// 데이터 전송 후 연결 종료
			dos.writeUTF(sendDataStr);
			dos.flush();
			dos.close();
			socket.close();
		}catch (Exception e) {
			e.printStackTrace();
			// 네트워크 에러로 관리자에게 결과 메일 전송
			if(sendData != null) {
				String title = "[네트워크 에러]"+ sendData.getBatPrmId()+ " 실행 결과";
				String content = "<h4>배치 관리 서버와 통신 에러가 발생하였으나 배치 프로그램은 실행이 완료되었습니다.</h4><br>"
								+ "<p><strong>실행 결과</strong></p><p>" + sendData.toString() + "</p>";
				mailService.sendMail(title, content);
			}else {
				String title = "[에러] 실행한 프로그램의 값이 정상적이지 않습니다.";
				String content = "<h4>프로그램은 실행되었으나 보낼 데이터가 정상적이지 않습니다.</h4><br>"
								+ "<p><strong>에러 메세지</strong></p><p>" + e.getMessage() + "</p>";
				mailService.sendMail(title, content);
			}
		}
	}
	
	/**
	 * 배치 관리 서버에서 받은 데이터를 파싱해 프로그램 실행 및 결과 반환
	 * @param socket 
	 * @throws IOException
	 */
	public void receiveMessage(Socket socket) {
		threadPool.execute(new Runnable() {
			@Override
			public void run() {
				try {
					DataInputStream dis = new DataInputStream(socket.getInputStream());
					
					String message = dis.readUTF();
					JSONObject jsonMessage = new JSONObject(message);

					if(jsonMessage.get("cmd").equals("path")) {				// 경로 요청
						sendPath(socket);
					}else if(jsonMessage.get("cmd").equals("healthCheck")) {					// 상태 체크
						healthCheck(socket);
					}else if(jsonMessage.get("cmd").equals("run")){			// 배치 실행
						ObjectMapper mapper = new ObjectMapper();
						List<JsonDto> receiveDataList = mapper.readValue(jsonMessage.get("message").toString(), new TypeReference<List<JsonDto>>() {});
						
						boolean error = false;
						for(JsonDto receiveData : receiveDataList) {
							log.info(receiveData.toString());
							JsonDto sendData = new JsonDto();
							sendData.setBatPrmStCd(BatchStatusCode.FAIL.getCode());
							if(!error) 	{
								sendData = runProgram(receiveData.getPath(), receiveData.getParam()); // 프로그램 실행
								if(sendData.getBatPrmStCd().equals(BatchStatusCode.FAIL.getCode())) error = true;
							}
							// 프로그램 실행 및 반환 -> 실패시 다음 순서 전부 실패
							sendData.getBatPrmStCd(); 
							// 마지막 순번 체크
							String last = "N";
							// 보낼 데이터 추가 정의
							sendData.setBatGrpLogId(receiveData.getBatGrpLogId());
							sendData.setBatGrpRtyCnt(receiveData.getBatGrpRtyCnt());
							sendData.setBatPrmId(receiveData.getBatPrmId());
							sendData.setExcnOrd(receiveData.getExcnOrd());
							if(receiveData.getExcnOrd() == receiveDataList.size()) last = "Y";
							sendData.setLastYn(last);
							
							
							// 관리 서버로 결과 전송
							sendMessage(sendData);
							
							
							socket.close();
							Thread.sleep(10000L);
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
					String title = "[네트워크 에러] 배치프로그램을 실행할 수 없습니다.";
					String content = "<h4>배치 관리서버에서 전송한 프로그램 정보를 받을 수 없거나 잘못된 형식입니다.</h4><br>"
									+ "<p><strong>발생 시간 : <strong>" +  new java.util.Date() + "</p>"
									+ "<p><strong>에러 메세지<strong></p><p>" + e.getMessage() + "</p>";
					mailService.sendMail(title, content);
				}
			}
		});
	}
			
	/**
	 * 배치 프로그램 실행 및 결과 반환
	 * @param path 실행파일 경로
	 * @param param 파라미터
	 * @return 실행 결과
	 */
	public JsonDto runProgram(String path, String param) {
		
		JsonDto jsonDto = new JsonDto();
		
		jsonDto.setBatBgngDt(new Date(System.currentTimeMillis()));
		try {
			// cmd[0] = 실행 파일 경로, cmd[1] = 파라미터 
			String[] cmd = new String[1];
			cmd[0] = path;
			// 파라미터 있으면 추가
			if(param != null && !param.equals("")) {
				cmd = new String[2];
				cmd[0] = path;
				cmd[1] = param;
				jsonDto.setParam(param);
			};
			
			// 확장자 분기처리 (bat, sh, jar)
			int lastDot = path.lastIndexOf(".");
			String extension = path.substring(lastDot + 1);
			if(extension.equals("jar")) {
				path = "java -jar" + path;
			}else if(extension.equals("sh")) {
				path = "sh" + path;
			}
			// 프로그램 실행
			Process process = Runtime.getRuntime().exec(cmd);
			BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));						
			String line = null;
			StringBuffer sb = new StringBuffer();
			
			// 실행한 결과 읽기
			while((line = br.readLine()) != null ) {
				sb.append(line);
			}
			log.info("[실행결과]: " + sb.toString());
			jsonDto.setRsltMsg(sb.toString());
			jsonDto.setBatPrmStCd(BatchStatusCode.SUCCESS.getCode());
			jsonDto.setBatEndDt(new Date(System.currentTimeMillis()));
		}catch(Exception e) {
			e.printStackTrace();
			String result = "[프로그램 실행 실패] " + e.getMessage();
			
			jsonDto.setRsltMsg(result);
			jsonDto.setBatPrmStCd(BatchStatusCode.FAIL.getCode());
			jsonDto.setBatEndDt(new Date(System.currentTimeMillis()));
		}
		return jsonDto;
	}
	
	/**
	 * 배치 파일 경로 전송
	 * @param socket
	 * @throws IOException
	 */
	public void sendPath(Socket socket) throws IOException {
		
		DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
		setPath(rootPath);
		
		dos.writeUTF(pathArray.toString());
		
		dos.flush();
		dos.close();
		socket.close();
		pathArray = new JSONArray();
	}
	
	/**
	 * 재귀호출해서 JSONArray에 저장
	 * @param object
	 * @return
	 */
    public void setPath(String strDirPath) { 
    	
        File temp = new File( strDirPath ); 
        File[] path = temp.listFiles(); 
         
        for( int i = 0; i < path.length; i++ ) { 
             
             
            if( path[i].isDirectory() ) { 
            	setPath(path[i].getPath() );  // 재귀함수 호출 
            }else if( path[i].isFile() ) { 
            	pathArray.put(path[i].getPath());
            }
        }
    } 
	
    /**
     * Agent 서버 통신 체크
     * @param socket
     * @throws IOException
     */
    public void healthCheck(Socket socket) throws IOException {
    	DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
    	log.info("health-check");
    	dos.writeUTF("on");
    	dos.flush();
    	dos.close();
    	socket.close();
    }
    
	public static void main(String[] args) throws IOException {
		BatchAgent batchAgent = new BatchAgent();
		

		batchAgent.start();
	}
}

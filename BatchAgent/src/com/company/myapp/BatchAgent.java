package com.company.myapp;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.company.myapp.code.BatchStatusCode;
import com.company.myapp.code.CommandCode;
import com.company.myapp.dto.JsonDto;
import com.company.myapp.service.MailService;
import com.fasterxml.jackson.core.JsonProcessingException;
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
	
	String ip; // 현재 서버 ip  
	int port; // 소켓 생성할 포트
	
	ExecutorService threadPool;
	ServerSocket serverSocket;
	
	JSONArray pathArray = new JSONArray();
	Properties properties = new Properties();
	
	/**
	 * 설정파일 정보 로드
	 * 스레드풀, 서버소켓 생성 및 연결 수락 
	 * @throws IOException
	 */	
	public void start() throws IOException {
		
		// 설정파일에 있는 정보 로드
		properties.load(BatchAgent.class.getResourceAsStream("/agent.properties"));
		//properties.load(BatchAgent.class.getResourceAsStream("/conf/agent.properties"));
		rootPath = properties.getProperty("agent.batch.path");
		managementPort = Integer.parseInt(properties.get("management.server.port").toString());
		managementIp = properties.getProperty("management.server.ip");
		
		// 내 아이피, 포트 셋팅
		InetAddress local = InetAddress.getLocalHost();
		ip = local.getHostAddress();
		port = Integer.parseInt(properties.get("agent.server.port").toString());
		int threadNum = Integer.parseInt(properties.get("threadNum").toString());
		
		System.setProperty("ip", ip);
		System.setProperty("port", String.valueOf(port));
		System.setProperty("managementIp", managementIp);
		log.info("[Agent 서버] 시작");
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
						log.error("[응답 에러] {}", e.getMessage());
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
		log.info("[Agent 서버] 종료");
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
		
		try(
			// 소켓 생성 및 배치 관리 서버 연결 요청
			Socket socket = new Socket(managementIp, managementPort);
			DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
		) {
			ObjectMapper mapper = new ObjectMapper();
			String message = mapper.writeValueAsString(sendData);
			JSONObject json = new JSONObject();
			json.put("cmd", CommandCode.LOG.getCode());
			json.put("message", message);
			String sendDataStr = json.toString();
			
			// 데이터 전송
			dos.writeUTF(sendDataStr);
			log.info("로그ID: '{}' / 프로그램ID: '{}' {}회차 배치 결과를 전송하였습니다.", 
					sendData.getBatGrpLogId(), sendData.getBatPrmId(), sendData.getBatGrpRtyCnt()+1);
		} catch (JsonProcessingException | NullPointerException e) {
			log.error("[결과 변환 에러] {}", e.getMessage());
			log.error("[보낼 데이터] {}", sendData);
		} catch (UnknownHostException | ConnectException e) {
			log.error("[SOCKET 생성 에러] {}", e.getMessage());
			log.error("[보낼 데이터] {}", sendData);
			MailService mailService = new MailService(sendData.getAdminEmail());
			// 네트워크 에러로 관리자에게 결과 메일 전송
			String title = "[AGENT SERVER] 네트워크 에러가 발생하였습니다.";
			String content = "<h4>배치 관리 서버 통신 에러로 실행 결과를 전송하지 못했습니다.</h4><br>"
							+ "<p><strong>호스트 정보 - "+ ip + ":" +port +"</strong></p>"
							+ "<p><strong>프로그램ID - "+ sendData.getBatPrmId() +"</strong></p>"
							+ "<p><strong>실행 결과 - " + sendData.toString() + "</strong></p>";
			mailService.sendMail(title, content);
		}  catch (Exception e) {
			log.error("[결과 전송 실패] {}", e.getMessage());
			log.error("[보낼 데이터] {}", sendData);
			MailService mailService = new MailService(sendData.getAdminEmail());
			// 네트워크 에러로 관리자에게 결과 메일 전송
			String title = "[AGENT SERVER] 네트워크 에러가 발생하였습니다.";
			String content = "<h4>배치 관리 서버 통신 에러로 실행 결과를 전송하지 못했습니다.</h4><br>"
							+ "<p><strong>호스트 정보 - "+ ip + ":" +port +"</strong></p>"
							+ "<p><strong>프로그램ID - "+ sendData.getBatPrmId() +"</strong></p>"
							+ "<p><strong>실행 결과 - " + sendData.toString() + "</strong></p>";
			mailService.sendMail(title, content);
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
					
					String request = dis.readUTF();
					JSONObject jsonMessage = new JSONObject(request);
					String cmd = jsonMessage.getString("cmd");
					
					if(cmd.equals(CommandCode.PATH.getCode())) {		// 경로 요청
						sendPath(socket, jsonMessage.getString("message"));
					}else if(cmd.equals(CommandCode.CHECK.getCode())) {	// 상태 체크
						healthCheck(socket);
					}else if(cmd.equals(CommandCode.RUN.getCode())){	// 배치 실행
						ObjectMapper mapper = new ObjectMapper();
						List<JsonDto> receiveDataList = mapper.readValue(jsonMessage.get("message").toString(), new TypeReference<List<JsonDto>>() {});
						boolean error = false;
						for(JsonDto receiveData : receiveDataList) {
							JsonDto sendData = new JsonDto();
							sendData.setAdminEmail(receiveData.getAdminEmail()); // 이메일
							sendData.setBatPrmStCd(BatchStatusCode.FAIL.getCode()); // 결과 코드 
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
						}
					}
				} catch(JSONException e) {
					log.error("[관리서버 메세지 형식 에러]", e.getMessage());
				} catch(IOException e) {
					log.error("[SOCKET 응답 에러]", e.getMessage());
				} catch(Exception e) {
					e.printStackTrace();
					log.error("[메세지 응답 에러]", e.getMessage());
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
		List<String> cmd = new ArrayList<>();
		jsonDto.setBatBgngDt(new Date(System.currentTimeMillis()));
		try {
			
			// 확장자 분기처리 (bat, sh, jar)
			int lastDot = path.lastIndexOf(".");
			String extension = path.substring(lastDot + 1);

			// 명령어 추가
			if(extension.equals("jar")) {
				cmd.add("java");
				cmd.add("-jar");
			}else if(extension.equals("sh")) {
				cmd.add("sh");
			}
			
			cmd.add(path);	// 경로 추가
			if(param != null && !param.equals(""))cmd.add(param);	//파라미터 있으면 추가
			ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);;	// ProcessBuilder 객체 생성
			Process process = pb.start();	// 실행
			
			// 프로그램 실행
			BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));						
			String line = null;
			StringBuffer sb = new StringBuffer();
			
			// 실행한 결과 읽기
			while((line = br.readLine()) != null ) {
				sb.append(line);
			}
			// 프로그램 실행 결과 결과 코드와 결과 메세지로 분리
			String[] resultArr = sb.toString().split(",");
			String result = null;
			
			// 형식 다를경우 대비
			if(resultArr.length == 1) {
				result = "배치 파일 응답 형식을 확인해주세요";
			}else {
				result = resultArr[1];	
			}
			
			if(resultArr[0].equals("1")) { // 결과 1이면 성공
				jsonDto.setBatPrmStCd(BatchStatusCode.SUCCESS.getCode());
				log.info("[{} 실행결과] {}", path, result);
			}else { // 0이면 실패
				jsonDto.setBatPrmStCd(BatchStatusCode.FAIL.getCode());
				log.error("[{} 실행결과] {}", path, result);
			}
			jsonDto.setRsltMsg(result);
			jsonDto.setBatEndDt(new Date(System.currentTimeMillis()));
			
			br.close();
			process.destroy();
		}catch(IOException e) {
		log.error("[프로그램 실행 에러]	{}", e.getMessage());
			String result = "[프로그램 실행 실패] " + e.getMessage();
			
			jsonDto.setRsltMsg(result);
			jsonDto.setBatPrmStCd(BatchStatusCode.FAIL.getCode());
			jsonDto.setBatEndDt(new Date(System.currentTimeMillis()));
		}
		return jsonDto;
	}
	
	/**
	 * 받은 경로의 하위 폴더, 파일 리턴
	 * @param socket
	 * @param message
	 */
	public void sendPath(Socket socket, String message) {
		try(DataOutputStream dos = new DataOutputStream(socket.getOutputStream());) {
		JSONObject result = new JSONObject();
		JSONArray file = new JSONArray();
		JSONArray dir = new JSONArray();
		
		File temp = new File(message);
		File[] path = temp.listFiles();
		
		for(File f : path) {
			if(f.isDirectory()) {
				dir.put(f.getPath());
			}else {
				file.put(f.getPath());
			}
		}
		
		result.put("dir", dir);
		result.put("file", file);
		
		dos.writeUTF(result.toString());
		
		socket.close();
		} catch(NullPointerException e) {
			log.error("[경로 에러] 일치하는 경로가 없습니다.");
		} catch(IOException e) {
			log.error("[연결 에러] 배치 관리 서버와 통신 에러가 발생해 경로를 보낼 수 없습니다.");
		}
	}
	
	
    /**
     * Agent 서버 통신 체크
     * @param socket
     * @throws IOException
     */
    public void healthCheck(Socket socket) throws IOException {
    	DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
    	dos.writeUTF("on");
    	dos.flush();
    	dos.close();
    	socket.close();
    }
    
	public static void main(String[] args) throws Exception {
		BatchAgent batchAgent = new BatchAgent();
		batchAgent.start();
	}
}

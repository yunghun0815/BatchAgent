package com.company.myapp.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Agent 서버와 통신간 메세지 구분 코드 
 * @author 정영훈, 김나영
 *
 */
@Getter
@RequiredArgsConstructor
public enum CommandCode {
	
	// 배치 관리 서버 -> Agent 서버 
	PATH("path","서버 배치 파일 경로 요청"),
	CHECK("healthCheck", "서버 연결 상태 체크"),
	RUN("run", "배치 파일 실행 요청"),
	
	// Agent 서버 -> 배치 관리 서버
	LOG("log", "배치 파일 실행 후 결과 정보");
	
	private final String code;
	private final String description;
	
}
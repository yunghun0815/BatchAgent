package com.company.myapp.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 배치 관리 서버 통신용 DTO
 * @author 정영훈, 김나영
 *
 */
@JsonIgnoreProperties(ignoreUnknown = true)	// 받아온 JSON이 NULL이어도 허용
@ToString
@Getter
@Setter
public class JsonDto {
	private String batGrpLogId; 	// 그룹 로그 아이디
	private int batGrpRtyCnt; 		// 그룹 로그 차수
	private String batPrmId; 		// 프로그램 아이디
	private String batPrmStCd; 		// 상태 코드
	private String rsltMsg; 		// 결과 메세지
	private int excnOrd; 			// 실행 순서
	private String path; 			// 배치파일 경로
	private String param; 			// 파라미터
	private Date batBgngDt; 		// 배치 시작 시간
	private Date batEndDt; 			// 배시 종료 시간
	private String lastYn;			// 마지막인지 체크
}

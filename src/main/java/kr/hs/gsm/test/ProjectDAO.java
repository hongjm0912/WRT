package kr.hs.gsm.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

/**
 * DB 접속/해제 공통 클래스.
 * UserDAO, VolunteerDAO, PointDAO 가 상속해서 사용한다.
 */
public class ProjectDAO {
	protected Connection conn;
	protected PreparedStatement ps;
	protected ResultSet rs;

	//DB접속
	public Connection getConn() {
		String url = "jdbc:oracle:thin:@127.0.0.1:1521:xe";	//DB접속 주소
		String user = "system";								//사용자계정
		String password = "1234";							//비밀번호
		try {
			Class.forName("oracle.jdbc.OracleDriver");
			conn = DriverManager.getConnection(url, user, password);
			System.out.println("DB 접속 완료!");
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("getConn() Exception!!!");
		}
		return conn;
	}//getConn()

	//DB접속 해제
	public void dbClose() {
		try {
			if(rs != null) rs.close();
			if(ps != null) ps.close();
			if(conn != null) conn.close();
			System.out.println("DB 접속 해제!");
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("dbClose() Exception!!!");
		}
	}//dbClose()

	//PreparedStatement 에 파라미터 순서대로 바인딩 (null 은 setNull 처리)
	protected void bind(PreparedStatement ps, List<Object> params) throws SQLException {
		for(int i = 0; i < params.size(); i++) {
			Object value = params.get(i);
			if(value == null) {
				ps.setNull(i + 1, Types.VARCHAR);
			} else {
				ps.setObject(i + 1, value);
			}
		}
	}//bind()

	//NULL 허용 숫자 컬럼 읽기
	protected Integer getIntOrNull(ResultSet rs, String column) throws SQLException {
		int value = rs.getInt(column);
		return rs.wasNull() ? null : value;
	}//getIntOrNull()

	protected Long getLongOrNull(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}//getLongOrNull()

	//시퀀스 다음 값 조회
	protected long nextVal(String sequenceName) throws SQLException {
		try (PreparedStatement seq = conn.prepareStatement("select " + sequenceName + ".nextval from dual");
			 ResultSet seqRs = seq.executeQuery()) {
			seqRs.next();
			return seqRs.getLong(1);
		}
	}//nextVal()

	//트랜잭션 롤백 (예외 발생 시)
	protected void rollback() {
		try {
			if(conn != null) conn.rollback();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}//rollback()

}//class

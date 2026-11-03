package kr.hs.gsm.test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

//users 테이블 + 학생 요약 집계
public class UserDAO extends ProjectDAO {

	private static final String SELECT_USER =
		  "select id, email, name, role, grade, class_no, student_no, "
		+ "to_char(created_at, 'YYYY-MM-DD HH24:MI:SS') as created_at "
		+ "from users u ";

	//승인 봉사 누적, 유효 상점·벌점 합계
	private static final String SELECT_SUMMARY =
		  "select u.id, u.name, "
		+ "nvl((select sum(v.minutes) from volunteers v "
		+ "     where v.student_id = u.id and v.status = 'APPROVED'), 0) as volunteer_minutes, "
		+ "nvl((select sum(p.score) from points p join point_items i on i.id = p.item_id "
		+ "     where p.student_id = u.id and p.status = 'ACTIVE' and i.type = 'MERIT'), 0) as merit_total, "
		+ "nvl((select sum(p.score) from points p join point_items i on i.id = p.item_id "
		+ "     where p.student_id = u.id and p.status = 'ACTIVE' and i.type = 'DEMERIT'), 0) as demerit_total "
		+ "from users u ";

	//학생 검색 조건 (alias u 기준) - PointDAO 의 필터 부여에서도 사용
	static String studentWhere(Integer grade, Integer classNo, String q, List<Object> params) {
		StringBuilder where = new StringBuilder("where u.role = 'STUDENT' ");
		if(grade != null) {
			where.append("and u.grade = ? ");
			params.add(grade);
		}
		if(classNo != null) {
			where.append("and u.class_no = ? ");
			params.add(classNo);
		}
		if(q != null && !q.isBlank()) {
			where.append("and (u.name like ? or u.email like ?) ");
			params.add("%" + q.trim() + "%");
			params.add("%" + q.trim() + "%");
		}
		return where.toString();
	}//studentWhere()

	private UserDTO toUser(ResultSet rs) throws SQLException {
		UserDTO dto = new UserDTO();
		dto.setId(rs.getLong("id"));
		dto.setEmail(rs.getString("email"));
		dto.setName(rs.getString("name"));
		dto.setRole(rs.getString("role"));
		dto.setGrade(getIntOrNull(rs, "grade"));
		dto.setClassNo(getIntOrNull(rs, "class_no"));
		dto.setStudentNo(getIntOrNull(rs, "student_no"));
		dto.setCreatedAt(rs.getString("created_at"));
		return dto;
	}//toUser()

	private StudentSummaryDTO toSummary(ResultSet rs) throws SQLException {
		StudentSummaryDTO dto = new StudentSummaryDTO();
		dto.setStudentId(rs.getLong("id"));
		dto.setName(rs.getString("name"));
		dto.setVolunteerMinutes(rs.getInt("volunteer_minutes"));
		dto.setMeritTotal(rs.getInt("merit_total"));
		dto.setDemeritTotal(rs.getInt("demerit_total"));
		return dto;
	}//toSummary()

	//사용자 1명 조회 (GET /me)
	public UserDTO getUser(long id) {
		UserDTO dto = null;
		try {
			conn = getConn();
			ps = conn.prepareStatement(SELECT_USER + "where u.id = ?");
			ps.setLong(1, id);
			rs = ps.executeQuery();
			if(rs.next()) dto = toUser(rs);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("getUser() Exception!!!");
		} finally {
			dbClose();
		}
		return dto;
	}//getUser()

	//이메일로 조회 (POST /auth/google - 구글 계정 이메일로 사용자 확인)
	public UserDTO getUserByEmail(String email) {
		UserDTO dto = null;
		try {
			conn = getConn();
			ps = conn.prepareStatement(SELECT_USER + "where u.email = ?");
			ps.setString(1, email);
			rs = ps.executeQuery();
			if(rs.next()) dto = toUser(rs);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("getUserByEmail() Exception!!!");
		} finally {
			dbClose();
		}
		return dto;
	}//getUserByEmail()

	//학생 목록 (GET /students?grade&class&q)
	public ArrayList<UserDTO> getStudentList(Integer grade, Integer classNo, String q) {
		ArrayList<UserDTO> list = new ArrayList<UserDTO>();
		List<Object> params = new ArrayList<Object>();
		String sql = SELECT_USER + studentWhere(grade, classNo, q, params)
				   + "order by u.grade, u.class_no, u.student_no";
		try {
			conn = getConn();
			ps = conn.prepareStatement(sql);
			bind(ps, params);
			rs = ps.executeQuery();
			while(rs.next()) list.add(toUser(rs));
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("getStudentList() Exception!!!");
		} finally {
			dbClose();
		}
		return list;
	}//getStudentList()

	//명단 업로드 (POST /users/import) - 일괄 등록, 성공 건수 반환 (하나라도 실패하면 전체 취소)
	public int importUsers(List<UserDTO> users) {
		int count = 0;
		String sql = "insert into users (id, email, name, role, grade, class_no, student_no) "
				   + "values (seq_users.nextval, ?, ?, ?, ?, ?, ?)";
		try {
			conn = getConn();
			conn.setAutoCommit(false);
			ps = conn.prepareStatement(sql);
			for(UserDTO u : users) {
				List<Object> params = new ArrayList<Object>();
				params.add(u.getEmail());
				params.add(u.getName());
				params.add(u.getRole());
				params.add(u.getGrade());
				params.add(u.getClassNo());
				params.add(u.getStudentNo());
				bind(ps, params);
				ps.addBatch();
			}
			ps.executeBatch();
			conn.commit();
			count = users.size();
		} catch (Exception e) {
			rollback();
			e.printStackTrace();
			System.out.println("importUsers() Exception!!!");
		} finally {
			dbClose();
		}
		return count;
	}//importUsers()

	//학생 요약 (GET /students/{id}/summary, GET /me 합계)
	public StudentSummaryDTO getStudentSummary(long studentId) {
		StudentSummaryDTO dto = null;
		try {
			conn = getConn();
			ps = conn.prepareStatement(SELECT_SUMMARY + "where u.id = ?");
			ps.setLong(1, studentId);
			rs = ps.executeQuery();
			if(rs.next()) dto = toSummary(rs);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("getStudentSummary() Exception!!!");
		} finally {
			dbClose();
		}
		return dto;
	}//getStudentSummary()

	//학년·반 학생별 요약 목록 (GET /stats)
	public ArrayList<StudentSummaryDTO> getSummaryList(Integer grade, Integer classNo) {
		ArrayList<StudentSummaryDTO> list = new ArrayList<StudentSummaryDTO>();
		List<Object> params = new ArrayList<Object>();
		String sql = SELECT_SUMMARY + studentWhere(grade, classNo, null, params)
				   + "order by u.grade, u.class_no, u.student_no";
		try {
			conn = getConn();
			ps = conn.prepareStatement(sql);
			bind(ps, params);
			rs = ps.executeQuery();
			while(rs.next()) list.add(toSummary(rs));
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("getSummaryList() Exception!!!");
		} finally {
			dbClose();
		}
		return list;
	}//getSummaryList()

}//class

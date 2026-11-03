package kr.hs.gsm.test;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

//volunteers, volunteer_files 테이블
public class VolunteerDAO extends ProjectDAO {

	private static final String SELECT_VOLUNTEER =
		  "select v.id, v.student_id, v.org, to_char(v.vol_date, 'YYYY-MM-DD') as vol_date, "
		+ "v.minutes, v.content, v.status, v.reject_reason, v.reviewer_id, "
		+ "to_char(v.reviewed_at, 'YYYY-MM-DD HH24:MI:SS') as reviewed_at, "
		+ "to_char(v.created_at, 'YYYY-MM-DD HH24:MI:SS') as created_at, "
		+ "u.name as student_name, u.grade, u.class_no, u.student_no, "
		+ "(select count(*) from volunteer_files f where f.volunteer_id = v.id) as file_count "
		+ "from volunteers v join users u on u.id = v.student_id ";

	//검색 조건 (alias v, u 기준)
	private String searchWhere(VolunteerSearchDTO s, List<Object> params) {
		StringBuilder where = new StringBuilder("where 1 = 1 ");
		if(s == null) return where.toString();
		if(s.getStudentId() != null) {
			where.append("and v.student_id = ? ");
			params.add(s.getStudentId());
		}
		if(s.getGrade() != null) {
			where.append("and u.grade = ? ");
			params.add(s.getGrade());
		}
		if(s.getClassNo() != null) {
			where.append("and u.class_no = ? ");
			params.add(s.getClassNo());
		}
		if(s.getStatus() != null && !s.getStatus().isBlank()) {
			where.append("and v.status = ? ");
			params.add(s.getStatus());
		}
		if(s.getFrom() != null && !s.getFrom().isBlank()) {
			where.append("and v.vol_date >= to_date(?, 'YYYY-MM-DD') ");
			params.add(s.getFrom());
		}
		if(s.getTo() != null && !s.getTo().isBlank()) {
			where.append("and v.vol_date <= to_date(?, 'YYYY-MM-DD') ");
			params.add(s.getTo());
		}
		if(s.getOrg() != null && !s.getOrg().isBlank()) {
			where.append("and v.org like ? ");
			params.add("%" + s.getOrg().trim() + "%");
		}
		if(s.getMinMinutes() != null) {
			where.append("and v.minutes >= ? ");
			params.add(s.getMinMinutes());
		}
		if(s.getMaxMinutes() != null) {
			where.append("and v.minutes <= ? ");
			params.add(s.getMaxMinutes());
		}
		if(s.getHasFile() != null) {
			where.append(s.getHasFile() ? "and " : "and not ");
			where.append("exists (select 1 from volunteer_files f where f.volunteer_id = v.id) ");
		}
		return where.toString();
	}//searchWhere()

	private VolunteerDTO toVolunteer(ResultSet rs) throws SQLException {
		VolunteerDTO dto = new VolunteerDTO();
		dto.setId(rs.getLong("id"));
		dto.setStudentId(rs.getLong("student_id"));
		dto.setOrg(rs.getString("org"));
		dto.setVolDate(rs.getString("vol_date"));
		dto.setMinutes(rs.getInt("minutes"));
		dto.setContent(rs.getString("content"));
		dto.setStatus(rs.getString("status"));
		dto.setRejectReason(rs.getString("reject_reason"));
		dto.setReviewerId(getLongOrNull(rs, "reviewer_id"));
		dto.setReviewedAt(rs.getString("reviewed_at"));
		dto.setCreatedAt(rs.getString("created_at"));
		dto.setStudentName(rs.getString("student_name"));
		dto.setGrade(getIntOrNull(rs, "grade"));
		dto.setClassNo(getIntOrNull(rs, "class_no"));
		dto.setStudentNo(getIntOrNull(rs, "student_no"));
		dto.setFileCount(rs.getInt("file_count"));
		return dto;
	}//toVolunteer()

	//봉사 목록 (GET /volunteers, GET /exports/volunteers.xlsx)
	public ArrayList<VolunteerDTO> getVolunteerList(VolunteerSearchDTO search) {
		ArrayList<VolunteerDTO> list = new ArrayList<VolunteerDTO>();
		List<Object> params = new ArrayList<Object>();
		String sql = SELECT_VOLUNTEER + searchWhere(search, params)
				   + "order by v.vol_date desc, v.id desc";
		try {
			conn = getConn();
			ps = conn.prepareStatement(sql);
			bind(ps, params);
			rs = ps.executeQuery();
			while(rs.next()) list.add(toVolunteer(rs));
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("getVolunteerList() Exception!!!");
		} finally {
			dbClose();
		}
		return list;
	}//getVolunteerList()

	//봉사 1건 조회 (본인 확인·상태 확인용)
	public VolunteerDTO getVolunteer(long id) {
		VolunteerDTO dto = null;
		try {
			conn = getConn();
			ps = conn.prepareStatement(SELECT_VOLUNTEER + "where v.id = ?");
			ps.setLong(1, id);
			rs = ps.executeQuery();
			if(rs.next()) dto = toVolunteer(rs);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("getVolunteer() Exception!!!");
		} finally {
			dbClose();
		}
		return dto;
	}//getVolunteer()

	//봉사 등록 (POST /volunteers) - status 는 DRAFT(임시저장) 또는 SUBMITTED(제출), 새 id 반환 (실패 0)
	public long insertVolunteer(VolunteerDTO dto) {
		long id = 0;
		String sql = "insert into volunteers (id, student_id, org, vol_date, minutes, content, status) "
				   + "values (?, ?, ?, to_date(?, 'YYYY-MM-DD'), ?, ?, ?)";
		try {
			conn = getConn();
			long newId = nextVal("seq_volunteers");
			List<Object> params = new ArrayList<Object>();
			params.add(newId);
			params.add(dto.getStudentId());
			params.add(dto.getOrg());
			params.add(dto.getVolDate());
			params.add(dto.getMinutes());
			params.add(dto.getContent());
			params.add(dto.getStatus() == null ? "DRAFT" : dto.getStatus());
			ps = conn.prepareStatement(sql);
			bind(ps, params);
			if(ps.executeUpdate() == 1) id = newId;
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("insertVolunteer() Exception!!!");
		} finally {
			dbClose();
		}
		return id;
	}//insertVolunteer()

	//임시저장 건 수정 (PATCH /volunteers/{id}) - 본인 + DRAFT 일 때만, 수정 건수 반환 (0 = 권한 없음/제출됨)
	//status 에 SUBMITTED 를 넣으면 제출 처리
	public int updateVolunteer(VolunteerDTO dto) {
		int result = 0;
		String sql = "update volunteers set org = ?, vol_date = to_date(?, 'YYYY-MM-DD'), "
				   + "minutes = ?, content = ?, status = ? "
				   + "where id = ? and student_id = ? and status = 'DRAFT'";
		try {
			conn = getConn();
			List<Object> params = new ArrayList<Object>();
			params.add(dto.getOrg());
			params.add(dto.getVolDate());
			params.add(dto.getMinutes());
			params.add(dto.getContent());
			params.add(dto.getStatus() == null ? "DRAFT" : dto.getStatus());
			params.add(dto.getId());
			params.add(dto.getStudentId());
			ps = conn.prepareStatement(sql);
			bind(ps, params);
			result = ps.executeUpdate();
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("updateVolunteer() Exception!!!");
		} finally {
			dbClose();
		}
		return result;
	}//updateVolunteer()

	//승인 (POST /volunteers/{id}/approve) - SUBMITTED 일 때만
	public int approveVolunteer(long id, long reviewerId) {
		int result = 0;
		String sql = "update volunteers set status = 'APPROVED', reviewer_id = ?, reviewed_at = systimestamp "
				   + "where id = ? and status = 'SUBMITTED'";
		try {
			conn = getConn();
			ps = conn.prepareStatement(sql);
			ps.setLong(1, reviewerId);
			ps.setLong(2, id);
			result = ps.executeUpdate();
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("approveVolunteer() Exception!!!");
		} finally {
			dbClose();
		}
		return result;
	}//approveVolunteer()

	//반려 (POST /volunteers/{id}/reject) - reason 필수, SUBMITTED 일 때만
	public int rejectVolunteer(long id, long reviewerId, String reason) {
		int result = 0;
		if(reason == null || reason.isBlank()) return 0;
		String sql = "update volunteers set status = 'REJECTED', reject_reason = ?, reviewer_id = ?, reviewed_at = systimestamp "
				   + "where id = ? and status = 'SUBMITTED'";
		try {
			conn = getConn();
			ps = conn.prepareStatement(sql);
			ps.setString(1, reason.trim());
			ps.setLong(2, reviewerId);
			ps.setLong(3, id);
			result = ps.executeUpdate();
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("rejectVolunteer() Exception!!!");
		} finally {
			dbClose();
		}
		return result;
	}//rejectVolunteer()

	//일괄 승인 - ids 배열 방식 (POST /volunteers/bulk-approve), 승인 건수 반환
	public int bulkApprove(List<Long> ids, long reviewerId) {
		int result = 0;
		if(ids == null || ids.isEmpty()) return 0;
		String sql = "update volunteers set status = 'APPROVED', reviewer_id = ?, reviewed_at = systimestamp "
				   + "where id = ? and status = 'SUBMITTED'";
		try {
			conn = getConn();
			conn.setAutoCommit(false);
			ps = conn.prepareStatement(sql);
			for(Long id : ids) {
				ps.setLong(1, reviewerId);
				ps.setLong(2, id);
				result += ps.executeUpdate();
			}
			conn.commit();
		} catch (Exception e) {
			rollback();
			result = 0;
			e.printStackTrace();
			System.out.println("bulkApprove(ids) Exception!!!");
		} finally {
			dbClose();
		}
		return result;
	}//bulkApprove()

	//일괄 승인 - filter 객체 방식, 조건에 맞는 SUBMITTED 건만 승인
	public int bulkApprove(VolunteerSearchDTO filter, long reviewerId) {
		int result = 0;
		List<Object> params = new ArrayList<Object>();
		params.add(reviewerId);
		String sql = "update volunteers set status = 'APPROVED', reviewer_id = ?, reviewed_at = systimestamp "
				   + "where status = 'SUBMITTED' and id in ("
				   + "select v.id from volunteers v join users u on u.id = v.student_id "
				   + searchWhere(filter, params) + ")";
		try {
			conn = getConn();
			ps = conn.prepareStatement(sql);
			bind(ps, params);
			result = ps.executeUpdate();
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("bulkApprove(filter) Exception!!!");
		} finally {
			dbClose();
		}
		return result;
	}//bulkApprove()

	//증빙 파일 등록 (POST /volunteers/{id}/files) - 새 id 반환
	public long insertFile(VolunteerFileDTO dto) {
		long id = 0;
		String sql = "insert into volunteer_files (id, volunteer_id, file_name, file_path) values (?, ?, ?, ?)";
		try {
			conn = getConn();
			long newId = nextVal("seq_volunteer_files");
			ps = conn.prepareStatement(sql);
			ps.setLong(1, newId);
			ps.setLong(2, dto.getVolunteerId());
			ps.setString(3, dto.getFileName());
			ps.setString(4, dto.getFilePath());
			if(ps.executeUpdate() == 1) id = newId;
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("insertFile() Exception!!!");
		} finally {
			dbClose();
		}
		return id;
	}//insertFile()

	//봉사 건의 증빙 파일 목록
	public ArrayList<VolunteerFileDTO> getFileList(long volunteerId) {
		ArrayList<VolunteerFileDTO> list = new ArrayList<VolunteerFileDTO>();
		String sql = "select id, volunteer_id, file_name, file_path, "
				   + "to_char(uploaded_at, 'YYYY-MM-DD HH24:MI:SS') as uploaded_at "
				   + "from volunteer_files where volunteer_id = ? order by id";
		try {
			conn = getConn();
			ps = conn.prepareStatement(sql);
			ps.setLong(1, volunteerId);
			rs = ps.executeQuery();
			while(rs.next()) {
				VolunteerFileDTO dto = new VolunteerFileDTO();
				dto.setId(rs.getLong("id"));
				dto.setVolunteerId(rs.getLong("volunteer_id"));
				dto.setFileName(rs.getString("file_name"));
				dto.setFilePath(rs.getString("file_path"));
				dto.setUploadedAt(rs.getString("uploaded_at"));
				list.add(dto);
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("getFileList() Exception!!!");
		} finally {
			dbClose();
		}
		return list;
	}//getFileList()

}//class

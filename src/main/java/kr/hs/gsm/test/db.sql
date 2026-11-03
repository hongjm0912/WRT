-- =====================================================================
-- 봉사·상벌점 관리 DB (Oracle)
-- 테이블: users, volunteers, volunteer_files, point_items, points
-- PK는 시퀀스로 발급 (Oracle 11g XE에서도 동작)
-- =====================================================================

-- ---------------------------------------------------------------------
-- 0. 기존 객체 삭제 (재실행용, 없으면 건너뜀)
-- ---------------------------------------------------------------------
BEGIN
	FOR t IN (SELECT table_name FROM user_tables
	          WHERE table_name IN ('POINTS', 'POINT_ITEMS', 'VOLUNTEER_FILES', 'VOLUNTEERS', 'USERS')) LOOP
		EXECUTE IMMEDIATE 'DROP TABLE ' || t.table_name || ' CASCADE CONSTRAINTS';
	END LOOP;
	FOR s IN (SELECT sequence_name FROM user_sequences
	          WHERE sequence_name IN ('SEQ_USERS', 'SEQ_VOLUNTEERS', 'SEQ_VOLUNTEER_FILES', 'SEQ_POINT_ITEMS', 'SEQ_POINTS')) LOOP
		EXECUTE IMMEDIATE 'DROP SEQUENCE ' || s.sequence_name;
	END LOOP;
END;
/

-- ---------------------------------------------------------------------
-- 1. 시퀀스
-- ---------------------------------------------------------------------
CREATE SEQUENCE seq_users           START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_volunteers      START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_volunteer_files START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_point_items     START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_points          START WITH 1 INCREMENT BY 1 NOCACHE;

-- ---------------------------------------------------------------------
-- 2. 테이블
-- ---------------------------------------------------------------------

-- 사용자 (학생·교사 공통, role로 구분)
CREATE TABLE users (
	id          NUMBER(10)     NOT NULL,
	email       VARCHAR2(100)  NOT NULL,
	name        VARCHAR2(50)   NOT NULL,
	role        VARCHAR2(10)   NOT NULL,
	grade       NUMBER(1),                  -- 학년 (교사는 NULL)
	class_no    NUMBER(2),                  -- 반
	student_no  NUMBER(2),                  -- 번호
	created_at  TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
	CONSTRAINT pk_users       PRIMARY KEY (id),
	CONSTRAINT uk_users_email UNIQUE (email),
	CONSTRAINT ck_users_role  CHECK (role IN ('STUDENT', 'TEACHER'))
);

-- 봉사 기록
CREATE TABLE volunteers (
	id             NUMBER(10)      NOT NULL,
	student_id     NUMBER(10)      NOT NULL,   -- 등록 학생
	org            VARCHAR2(100)   NOT NULL,   -- 봉사 기관
	vol_date       DATE            NOT NULL,   -- 봉사일
	minutes        NUMBER(5)       NOT NULL,   -- 봉사 시간(분)
	content        VARCHAR2(1000),
	status         VARCHAR2(10)    DEFAULT 'DRAFT' NOT NULL,
	reject_reason  VARCHAR2(500),
	reviewer_id    NUMBER(10),                 -- 승인·반려 교사
	reviewed_at    TIMESTAMP,
	created_at     TIMESTAMP       DEFAULT SYSTIMESTAMP NOT NULL,
	CONSTRAINT pk_volunteers          PRIMARY KEY (id),
	CONSTRAINT fk_volunteers_student  FOREIGN KEY (student_id)  REFERENCES users (id),
	CONSTRAINT fk_volunteers_reviewer FOREIGN KEY (reviewer_id) REFERENCES users (id),
	CONSTRAINT ck_volunteers_status   CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED')),
	CONSTRAINT ck_volunteers_minutes  CHECK (minutes > 0),
	CONSTRAINT ck_volunteers_reject   CHECK (status <> 'REJECTED' OR reject_reason IS NOT NULL)
);

-- 봉사 증빙 파일
CREATE TABLE volunteer_files (
	id            NUMBER(10)     NOT NULL,
	volunteer_id  NUMBER(10)     NOT NULL,
	file_name     VARCHAR2(255)  NOT NULL,   -- 원본 파일명
	file_path     VARCHAR2(500)  NOT NULL,   -- 저장 경로
	uploaded_at   TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
	CONSTRAINT pk_volunteer_files PRIMARY KEY (id),
	CONSTRAINT fk_files_volunteer FOREIGN KEY (volunteer_id) REFERENCES volunteers (id) ON DELETE CASCADE
);

-- 상벌점 항목
CREATE TABLE point_items (
	id         NUMBER(10)     NOT NULL,
	name       VARCHAR2(100)  NOT NULL,
	type       VARCHAR2(10)   NOT NULL,              -- MERIT(상점) / DEMERIT(벌점)
	score      NUMBER(3)      NOT NULL,
	is_active  CHAR(1)        DEFAULT 'Y' NOT NULL,  -- Y / N
	CONSTRAINT pk_point_items        PRIMARY KEY (id),
	CONSTRAINT ck_point_items_type   CHECK (type IN ('MERIT', 'DEMERIT')),
	CONSTRAINT ck_point_items_score  CHECK (score > 0),
	CONSTRAINT ck_point_items_active CHECK (is_active IN ('Y', 'N'))
);

-- 상벌점 기록
CREATE TABLE points (
	id             NUMBER(10)     NOT NULL,
	student_id     NUMBER(10)     NOT NULL,   -- 대상 학생
	item_id        NUMBER(10)     NOT NULL,
	teacher_id     NUMBER(10)     NOT NULL,   -- 부여 교사 (취소 권한)
	semester       VARCHAR2(10)   NOT NULL,   -- 예: 2026-2
	score          NUMBER(3)      NOT NULL,   -- 부여 당시 항목 점수 복사
	memo           VARCHAR2(500),
	status         VARCHAR2(10)   DEFAULT 'ACTIVE' NOT NULL,
	cancel_reason  VARCHAR2(500),
	canceled_at    TIMESTAMP,
	created_at     TIMESTAMP      DEFAULT SYSTIMESTAMP NOT NULL,
	CONSTRAINT pk_points          PRIMARY KEY (id),
	CONSTRAINT fk_points_student  FOREIGN KEY (student_id) REFERENCES users (id),
	CONSTRAINT fk_points_item     FOREIGN KEY (item_id)    REFERENCES point_items (id),
	CONSTRAINT fk_points_teacher  FOREIGN KEY (teacher_id) REFERENCES users (id),
	CONSTRAINT ck_points_status   CHECK (status IN ('ACTIVE', 'CANCELED')),
	CONSTRAINT ck_points_cancel   CHECK (status <> 'CANCELED' OR cancel_reason IS NOT NULL)
);

-- 조회용 인덱스 (FK·필터 컬럼)
CREATE INDEX ix_volunteers_student ON volunteers (student_id);
CREATE INDEX ix_volunteers_status  ON volunteers (status);
CREATE INDEX ix_files_volunteer    ON volunteer_files (volunteer_id);
CREATE INDEX ix_points_student     ON points (student_id, semester);
CREATE INDEX ix_users_class        ON users (grade, class_no);

-- ---------------------------------------------------------------------
-- 3. 샘플 데이터
-- ---------------------------------------------------------------------

-- 교사 (id 1, 2)
INSERT INTO users (id, email, name, role) VALUES (seq_users.NEXTVAL, 'teacher1@gsm.hs.kr', '김선생', 'TEACHER');
INSERT INTO users (id, email, name, role) VALUES (seq_users.NEXTVAL, 'teacher2@gsm.hs.kr', '이선생', 'TEACHER');

-- 학생 (id 3 ~ 6)
INSERT INTO users (id, email, name, role, grade, class_no, student_no) VALUES (seq_users.NEXTVAL, 's25001@gsm.hs.kr', '김행복', 'STUDENT', 2, 2, 1);
INSERT INTO users (id, email, name, role, grade, class_no, student_no) VALUES (seq_users.NEXTVAL, 's25002@gsm.hs.kr', '이축복', 'STUDENT', 2, 2, 2);
INSERT INTO users (id, email, name, role, grade, class_no, student_no) VALUES (seq_users.NEXTVAL, 's25003@gsm.hs.kr', '장믿음', 'STUDENT', 2, 1, 3);
INSERT INTO users (id, email, name, role, grade, class_no, student_no) VALUES (seq_users.NEXTVAL, 's25004@gsm.hs.kr', '최사랑', 'STUDENT', 1, 3, 4);

-- 상벌점 항목 (id 1 ~ 4)
INSERT INTO point_items (id, name, type, score) VALUES (seq_point_items.NEXTVAL, '봉사활동 우수', 'MERIT', 2);
INSERT INTO point_items (id, name, type, score) VALUES (seq_point_items.NEXTVAL, '교내 환경미화', 'MERIT', 1);
INSERT INTO point_items (id, name, type, score) VALUES (seq_point_items.NEXTVAL, '지각', 'DEMERIT', 1);
INSERT INTO point_items (id, name, type, score) VALUES (seq_point_items.NEXTVAL, '수업 중 휴대폰 사용', 'DEMERIT', 2);

-- 봉사 기록 (id 1 ~ 4)
INSERT INTO volunteers (id, student_id, org, vol_date, minutes, content, status)
VALUES (seq_volunteers.NEXTVAL, 3, '광주시 복지관', TO_DATE('2026-10-11', 'YYYY-MM-DD'), 180, '어르신 배식 봉사', 'SUBMITTED');

INSERT INTO volunteers (id, student_id, org, vol_date, minutes, content, status, reviewer_id, reviewed_at)
VALUES (seq_volunteers.NEXTVAL, 3, '광산구 도서관', TO_DATE('2026-09-20', 'YYYY-MM-DD'), 120, '도서 정리', 'APPROVED', 1, SYSTIMESTAMP);

INSERT INTO volunteers (id, student_id, org, vol_date, minutes, content, status, reject_reason, reviewer_id, reviewed_at)
VALUES (seq_volunteers.NEXTVAL, 4, '지역아동센터', TO_DATE('2026-10-05', 'YYYY-MM-DD'), 240, '학습 멘토링', 'REJECTED', '증빙 서류 미비', 1, SYSTIMESTAMP);

INSERT INTO volunteers (id, student_id, org, vol_date, minutes, content, status)
VALUES (seq_volunteers.NEXTVAL, 5, '광주천 환경정화', TO_DATE('2026-11-01', 'YYYY-MM-DD'), 90, '하천 쓰레기 줍기', 'DRAFT');

-- 증빙 파일
INSERT INTO volunteer_files (id, volunteer_id, file_name, file_path) VALUES (seq_volunteer_files.NEXTVAL, 1, '봉사확인서.pdf', '/upload/volunteers/1/봉사확인서.pdf');
INSERT INTO volunteer_files (id, volunteer_id, file_name, file_path) VALUES (seq_volunteer_files.NEXTVAL, 2, '활동사진.jpg', '/upload/volunteers/2/활동사진.jpg');

-- 상벌점 기록 (score는 항목 점수 복사)
INSERT INTO points (id, student_id, item_id, teacher_id, semester, score, memo)
VALUES (seq_points.NEXTVAL, 3, 1, 1, '2026-2', 2, '복지관 봉사 우수');

INSERT INTO points (id, student_id, item_id, teacher_id, semester, score, memo)
VALUES (seq_points.NEXTVAL, 4, 2, 2, '2026-2', 1, '교실 환경미화');

INSERT INTO points (id, student_id, item_id, teacher_id, semester, score, memo)
VALUES (seq_points.NEXTVAL, 4, 3, 1, '2026-2', 1, '1교시 지각');

INSERT INTO points (id, student_id, item_id, teacher_id, semester, score, memo, status, cancel_reason, canceled_at)
VALUES (seq_points.NEXTVAL, 5, 4, 2, '2026-2', 2, '수업 중 휴대폰', 'CANCELED', '대상 학생 오입력', SYSTIMESTAMP);

COMMIT;

-- ---------------------------------------------------------------------
-- 4. 확인용 조회
-- ---------------------------------------------------------------------
SELECT * FROM users ORDER BY id;
SELECT * FROM volunteers ORDER BY id;
SELECT * FROM volunteer_files ORDER BY id;
SELECT * FROM point_items ORDER BY id;
SELECT * FROM points ORDER BY id;

-- 학생별 요약 (GET /students/{id}/summary 와 같은 집계)
SELECT u.id, u.name,
       NVL((SELECT SUM(v.minutes) FROM volunteers v
            WHERE v.student_id = u.id AND v.status = 'APPROVED'), 0) AS volunteer_minutes,
       NVL((SELECT SUM(p.score) FROM points p JOIN point_items i ON i.id = p.item_id
            WHERE p.student_id = u.id AND p.status = 'ACTIVE' AND i.type = 'MERIT'), 0) AS merit_total,
       NVL((SELECT SUM(p.score) FROM points p JOIN point_items i ON i.id = p.item_id
            WHERE p.student_id = u.id AND p.status = 'ACTIVE' AND i.type = 'DEMERIT'), 0) AS demerit_total
FROM users u
WHERE u.role = 'STUDENT'
ORDER BY u.id;

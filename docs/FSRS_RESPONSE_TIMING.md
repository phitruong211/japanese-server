# FSRS-6 và thời gian phản hồi

Ngày kiểm tra: 15/10/2026.

## Hiện trạng và phạm vi

Backend Spring Boot đã có `AnkiScheduler`, `anki_card_progress`, `anki_review_logs`, khóa duy nhất người dùng–thẻ, khóa khi đánh giá và mã chống gửi trùng. Dependency đang ghim `io.github.open-spaced-repetition:fsrs:1.0.0`; frontend ghim `ts-fsrs:5.4.2`. Không cài thêm một bộ lập lịch khác.

Thẻ nhập/thẻ cá nhân dùng UUID và bảng `cards`. Thẻ dựng sẵn dùng mã chuỗi, tiến độ nằm trong JSON `learning_states`; không tạo bản sao tiến độ trong `anki_card_progress`. Bổ sung `builtin_review_logs` vì thẻ dựng sẵn không có khóa ngoại tới `cards`.

Luồng đang được router sử dụng là `UnifiedDeckPage → StudySession`. Luồng này gửi đánh giá lên backend khi đăng nhập, bao gồm thẻ dựng sẵn. Chế độ khách vẫn dùng thư viện TypeScript và lưu lịch sử trong vùng lưu trữ khách. Các component phiên học cũ không được router sử dụng chưa được chuyển đổi.

## Mô hình và bằng chứng

FSRS dùng Difficulty (D), Stability (S) và Retrievability (R). S là khoảng thời gian để xác suất nhớ giảm còn 0,9. Với w > 0:

```text
k = 0.9^(-1/w) - 1
R(t,S) = (1 + k*t/S)^(-w)
interval = S/k * (targetRetention^(-1/w) - 1)
```

Công thức minh họa không thay thế thư viện. `AnkiScheduler.apply` gọi `Scheduler.reviewCard`; `predictedRecall` gọi `getCardRetrievability` trước khi cập nhật trạng thái. Tham số mặc định thứ 21 của Java là 0,2, độ suy giảm là âm của tham số đó. Java 1.0.0 dùng số ngày nguyên trong retrievability; `elapsedDays` trong log giữ số ngày thực để phân tích. Với thẻ mới hoặc trạng thái cũ thiếu stability, xác suất dự đoán được để null, không gán một xác suất giả.

Đầu vào: trạng thái trước ôn, rating, thời điểm server UTC, retention và các bước học trong cài đặt người dùng. Đầu ra: state, difficulty, stability, dueAt, learning step, repetitions, lapses. Retention mặc định hiện có là 0,90. Các trạng thái là NEW → LEARNING → REVIEW; quên thẻ REVIEW chuyển RELEARNING nếu có bước học lại. Hard là nhớ đúng nhưng khó; chỉ Again tính là không nhớ. Thời gian không phải tham số điều chỉnh rating.

Nguồn chính:

- [FSRS: thuật toán và công thức](https://github.com/open-spaced-repetition/awesome-fsrs/wiki/The-Algorithm).
- [Mã Scheduler đúng tag Java v1.0.0](https://raw.githubusercontent.com/open-spaced-repetition/java-fsrs/v1.0.0/src/main/java/io/github/openspacedrepetition/Scheduler.java): xác nhận 21 tham số và API thực tế.
- [Java-FSRS](https://github.com/open-spaced-repetition/java-fsrs): trạng thái, serialization, UTC, tối ưu tham số.
- [Pavlik & Anderson (2008), Using a Model to Compute the Optimal Schedule of Practice](https://digitalcommons.memphis.edu/facpubs/8906/): nghiên cứu mô hình hóa lịch luyện tập, có đánh giá độ chính xác và độ trễ nhớ lại. Đây không phải bằng chứng rằng phép chia thời gian cho baseline sẽ cải thiện FSRS trong ứng dụng này.

Diễn giải thiết kế: thời gian mặt trước gồm đọc câu hỏi và cố nhớ; thời gian mặt sau gồm đọc đáp án, học lại và chọn rating. Vì vậy chúng là chỉ báo hành vi có nhiễu, không phải phép đo trực tiếp độ đúng hay độ tự tin. Không áp ngưỡng giây chung để gán Hard/Easy.

## Đo lường và API

`reviewTiming.ts` dùng `performance.now()`; `useReviewTiming.ts` nối với visibility, focus/blur, pagehide/pageshow và trạng thái mở hộp thoại. Đồng hồ dừng khi không học. Thời gian gửi mạng không được cộng vào observation; lần gửi lại dùng cùng timing và rating.

`shownAt`, `revealedAt`, `ratedAt` là thời gian UTC phía trình duyệt để kiểm tra; khoảng thời gian thực học được cộng bằng đồng hồ đơn điệu. Khi bị tạm dừng, chênh lệch hai timestamp lớn hơn active duration, đây là điều bình thường. Timing bằng 0, âm, đồng hồ đảo chiều, quá giới hạn INTEGER hoặc dài hơn khoảng wall-clock tương ứng bị đánh dấu không hợp lệ. Đây là kiểm tra tính toàn vẹn phép đo, không phải ngưỡng đánh giá trí nhớ. Thời gian dài nhưng khả thi vẫn được giữ; outlier thống kê cần baseline cá nhân. Người dùng có thể ngừng chú ý mà không đổi tab; chưa có cách xác định chắc chắn sự kiện này.

API hiện có `POST /api/v1/anki/cards/{id}/reviews` nhận thêm các trường tùy chọn:

```json
{
  "clientReviewId": "UUID",
  "rating": "GOOD",
  "expectedRepetitions": 3,
  "shownAt": "2026-10-15T00:00:00Z",
  "revealedAt": "2026-10-15T00:00:05Z",
  "ratedAt": "2026-10-15T00:00:07Z",
  "recallTimeMs": 5000,
  "decisionTimeMs": 2000,
  "isTimeValid": true
}
```

`responseTimeMs` cũ vẫn được nhận nhưng không dùng để tính lịch. Client cũ chưa gửi timing được ghi `is_time_valid=false`. Client mới gửi số repetitions đã nhìn thấy; sai phiên bản trả 409. Retry kiểm tra mã trước phiên bản để trả lại kết quả đã lưu. Tái sử dụng mã với thẻ/rating khác trả 409. Lock và transaction bao trùm cập nhật progress cùng review log.

Thẻ dựng sẵn dùng `POST /api/v1/users/me/learning/reviews`, body `{cardId, deckType, review: <body ở trên>}`. Mã lượt ôn và expectedRepetitions là bắt buộc. Backend đọc tiến độ của chính tài khoản, dùng cùng AnkiScheduler, lưu snapshot cùng log trong transaction và trả `{state, card}`. Frontend tạm dừng đồng bộ snapshot, flush thay đổi trước đó, rồi áp dụng snapshot server trước khi chuyển thẻ. Khóa hàng người dùng tuần tự hóa các thao tác với JSON hiện có.

Hàng đợi backend thẻ cá nhân lọc `dueAt <= now`, ưu tiên RELEARNING, LEARNING, REVIEW rồi thêm thẻ mới theo giới hạn hiện có; fetch join card/deck tránh N+1. Hàng đợi frontend đưa thẻ học lại đúng hạn trở vào phiên và không đẩy thẻ review chưa đến hạn vào chỉ vì timing dài. Thẻ dựng sẵn vẫn lọc hàng đợi từ snapshot đã đồng bộ trong frontend; chưa có bảng catalog dựng sẵn phía server để truy vấn queue bằng SQL.

## File thay đổi

| Nhóm | File | Mục đích |
|---|---|---|
| Bộ lập lịch | `anki/AnkiScheduler.java` | Dự đoán trước ôn, định danh phiên bản, phục hồi thẻ learning cũ thiếu D/S |
| Đánh giá thẻ cá nhân | `anki/AnkiDtos.java`, `AnkiService.java`, `ReviewTiming.java` | Nhận/kiểm tra telemetry, chống cập nhật cũ, log trong transaction |
| Dữ liệu | `model/AnkiReviewLog.java`, `V14__review_timing.sql` | Thêm cột nullable, không bịa dữ liệu lịch sử |
| Hàng đợi | `repository/AnkiCardProgressRepository.java` | Ưu tiên học lại trong truy vấn đã fetch join |
| Thẻ dựng sẵn | `learning/LearningDtos.java`, `LearningController.java`, `LearningService.java`, `BuiltInSchedulerAdapter.java`, `BuiltInReviewLog.java`, `V15__builtin_review_logs.sql` | Lập lịch ở server, giữ JSON progress và lịch sử riêng theo mã chuỗi |
| Frontend | `src/lib/reviewTiming.ts`, `src/hooks/useReviewTiming.ts` | Đo hai pha, tạm dừng, cố định observation khi retry, hàm median baseline |
| Tích hợp UI/API | `StudySession.tsx`, `src/lib/api.ts`, `src/lib/learningSync.ts` | Gửi rating và telemetry; chỉ chuyển hàng đợi sau kết quả server |
| Test | `AnkiSchedulerTest`, `ReviewTimingTest`, `LearningServiceIntegrationTest`, `DeckServiceIntegrationTest`, `tests/reviewTiming.test.mjs` | Trạng thái, timing, tương thích và race |

Giảm min stability của DTO snapshot từ 0,1 xuống 0,001 cho đúng giới hạn của thư viện; không sửa giá trị đã lưu.

## Chuẩn hóa và nghiên cứu tiếp

`relativeRecallTime` tính median từ mẫu hữu hạn, dương và trả null khi chưa đủ mẫu. Người gọi phải cung cấp lịch sử hợp lệ của đúng người học và nhóm thẻ tương đương, chỉ gồm dữ liệu trước observation đang đánh giá. Số mẫu tối thiểu là cấu hình nghiên cứu bắt buộc truyền vào, không có ngưỡng mặc định tùy tiện. Hàm này chưa nối vào scheduler hoặc dashboard.

Thực nghiệm đề xuất:

1. Nhóm theo người học, loại thẻ, hướng hỏi/đáp, độ dài nội dung và hình thức đọc/nghe. Theo dõi phân phối recall và decision riêng; không mặc định duration dài là sai.
2. Nhãn nhớ: Again=0; Hard/Good/Easy=1. Bỏ thẻ mới và log không có dự đoán khỏi Brier/Log Loss. Báo cáo riêng review dài hạn và learning trong ngày.
3. A là FSRS chuẩn; B bổ sung timing của các lượt trước để dự đoán lần ôn sau. Nếu dùng timing của chính lượt đang được chấm để dự đoán nó, đó là bài toán khác và dễ gây rò rỉ dữ liệu.
4. Chia tập theo thời gian trong mỗi người, giữ test tương lai; baseline và tham số chỉ fit trên train. Báo cáo calibration, Brier Score, Log Loss, recall rate, số lượt ôn và tổng thời gian active. Không so model bằng accuracy đơn lẻ.
5. Chỉ thay lịch sau kiểm chứng ngoài mẫu và thử nghiệm ngẫu nhiên theo người học có tiêu chí dừng và khoảng tin cậy. Hiện chưa có bằng chứng thực nghiệm trong dữ liệu ứng dụng để bật B.

## Kiểm tra và giới hạn triển khai

Frontend: `npm test`, `npm run build`, `npm run lint` trong `FE/N3`. Backend: Java 21, `mvnw.cmd -Dtest=AnkiSchedulerTest,ReviewTimingTest,LearningServiceIntegrationTest,DeckServiceIntegrationTest test`. Trong sandbox Windows, Mockito tự attach gặp lỗi pipe; chạy với `-DargLine=-javaagent:<đường dẫn mockito-core-5.23.0.jar>` giải quyết việc này.

Migration V14/V15 cần chạy bằng Flyway khi khởi động backend. Chưa chạy migration trên database triển khai. Test tích hợp sử dụng H2 create-drop, không chứng minh tương thích migration MySQL hoặc tải production. Cần kiểm tra bản sao DB MySQL trước phát hành.

Các giới hạn giữ tương thích: endpoint PUT snapshot cũ và migration khách vẫn nhận trạng thái SRS, nên chưa khóa toàn bộ đường ghi lịch theo mô hình server-only. Log khách nằm ở thiết bị, chưa chuyển cùng dữ liệu khách khi đăng nhập. Mã retry hiện sống trong phiên UI, chưa có outbox bền vững qua reload. Giới hạn số thẻ mới/ngày vẫn theo cơ chế hàng đợi hiện có, chưa được enforce riêng ở endpoint review. Thẻ dựng sẵn cập nhật JSON O(n) theo số tiến độ tài khoản và khóa theo người dùng; không tuyên bố đã đạt quy mô lớn cho đường này. Những thay đổi về catalog, outbox và mô hình lưu trữ cần thiết kế riêng thay vì âm thầm thay kiến trúc.

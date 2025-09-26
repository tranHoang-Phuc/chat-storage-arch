# Chat Storage Architecture – Hướng dẫn cho người mới

Tài liệu này giúp bạn nhanh chóng định hình dự án trước khi bắt đầu đọc mã nguồn.

## 1. Ứng dụng làm gì?

Hệ thống cung cấp API ghi/đọc tin nhắn trò chuyện với hai lớp lưu trữ:

1. **L0 (hot)** – Ghi cực nhanh bằng Content-Addressed Storage (CAS) trên S3.
2. **L1 (cold)** – Gộp nhiều bản ghi thành "segment" nén để tiết kiệm chi phí.

Redis đảm nhiệm sinh số thứ tự hội thoại + idempotency, SQL Server lưu metadata, Kafka thông báo cho tiến trình nén.

## 2. Cấu trúc thư mục chính

```
src/main/java/com/sds/phucth/chatstoragearch/
├── ChatStorageArchApplication.java   # Điểm khởi động Spring Boot
├── consts/                           # Hằng số prefix, cấu trúc key, tham số
├── controllers/                      # API REST (MessageController)
├── dto/                              # Đối tượng trao đổi dữ liệu (ChatRecord, SegRequest...)
├── models/                           # Entity JPA (MessageRef)
├── repository/                       # Tầng truy cập dữ liệu (MessageRefRepository)
├── services/                         # Nơi chứa business logic (L0Writer, Reader, Compactor...)
└── utils/                            # Hàm tiện ích: JSON chuẩn hóa, Zstd, hash, ULID
```

Các cấu hình Spring nằm ở `src/main/resources/application.yaml` và package `config`.

## 3. Các luồng quan trọng

### 3.1 Ghi tin nhắn (L0WriterService)
1. Kiểm tra idempotency bằng Redis (client gửi clientMsgId).
2. Tăng số thứ tự hội thoại (`ConversationSequenceService`).
3. Chuẩn hóa JSON và nén bằng Zstd, upload lên S3 với khóa CAS (`S3Objects.casKey`).
4. Lưu bản ghi metadata vào SQL Server (`MessageRefRepository`).
5. Gửi sự kiện Kafka để tiến trình nén xử lý tiếp.

### 3.2 Nén sang L1 (CompactorService)
1. Scheduler lấy các bản ghi đủ "già" (`findEligibleForCompaction`).
2. Gom nhóm theo hội thoại, tạo segment ~16MB.
3. Đọc dữ liệu CAS, nén lại từng record thành frame và ghi file segment + index lên S3.
4. Lưu map `segUlid -> s3Key` vào Redis.
5. Cập nhật `refId` của bản ghi sang dạng `seg:<ulid>:<offset>:<length>`.

### 3.3 Đọc tin nhắn (ReaderService)
1. Truy vấn danh sách `MessageRef` theo window/limit.
2. Phân loại reference: CAS hay SEG.
3. Tải dữ liệu song song: CAS đọc trực tiếp, SEG đọc theo range và giải nén.
4. Ghép kết quả theo thứ tự gốc rồi trả về cho controller.

## 4. Các thành phần hỗ trợ khác

- **S3Service**: Quản lý `put/get/rangeGet`, tự cấu hình KMS/AES256, vùng, credential.
- **IdempotencyService**: Sử dụng Redis để lưu map `clientMsgId -> messageId`.
- **ConversationSequenceService**: Redis `INCR` để sinh `seq` cho mỗi hội thoại.
- **CanonicalJson/ZstdCodec/Hashing**: Đảm bảo nội dung ghi vào CAS có định dạng thống nhất và nén hiệu quả.

## 5. Những điều cần chú ý khi đọc mã

- **Prefix & refId**: Các hằng số trong package `consts` quyết định format key trên S3 và Redis.
- **MessageRef**: Entity trung tâm, giữ `refId` (CAS hoặc SEG). Đọc/ghi đều đi qua bảng này.
- **Executor trong ReaderService**: Xử lý song song, nhớ chú ý tới tài nguyên thread khi mở rộng.
- **Error handling**: Dự án log lỗi và ném RuntimeException – khi production cần chuẩn hóa hơn.

## 6. Gợi ý học tiếp

1. **Spring Boot cơ bản** (dependency injection, `@Service`, `@Repository`, cấu hình YAML).
2. **JPA/Hibernate** để hiểu cách `MessageRefRepository` truy vấn dữ liệu.
3. **Redis**: lệnh `INCR`, `SETNX`, TTL để triển khai idempotency/sequence.
4. **AWS S3 + KMS**: quyền IAM, thao tác `putObject`, `getObject`, range request.
5. **Kafka**: tạo topic, producer, consumer cho pipeline nén.
6. **Thuật toán nén Zstd** và quản lý buffer khi làm việc với dữ liệu lớn.
7. **ULID**: cơ chế tạo ID theo thời gian giúp sort ổn định.

## 7. Lộ trình đọc mã gợi ý

1. Bắt đầu từ `MessageController` để thấy API.
2. Theo luồng gọi vào `L0WriterService` và `ReaderService`.
3. Đọc `CompactorService` để hiểu cách chuyển L0 -> L1.
4. Xem `utils/` và `consts/` khi cần biết format cụ thể.

Chúc bạn học tốt! Khi đã quen, hãy thử chạy `./mvnw test` và docker-compose để kiểm tra toàn bộ pipeline.

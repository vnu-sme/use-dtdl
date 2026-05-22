# use-dtdl
A USE plugin for Digital Twins Definition Language


### Cài đặt công cụ lần đầu
Để cài đặt công cụ, trước tiên clone toàn bộ repo về. Sau đó, ở thư mục tổng, chạy lệnh dưới đây:
```
mvn clean install
```
Lệnh này sẽ build toàn bộ repo. Sau đó ở đường dẫn `use-dtdl/target/use-dtdl-7.1.1.jar`, copy file này vào đường dẫn `use-assembly/src/main/resources/plugins` để có thể compile plugin này lúc runtime.

Tiếp tục build lại `use-assembly` để có được file `use-7.1.1.zip` bằng câu lệnh dưới đây:
```
mvn package -pl use-assembly
```

Có file zip rồi ta giải nén sẽ được 3 file sau:

<img width="642" height="83" alt="image" src="https://github.com/user-attachments/assets/5c802230-e3ff-4e80-9df6-20833a53a205" />

Chạy file `.bat` để khởi tạo công cụ USE. Màn hình hiển thị như sau:

<img width="905" height="654" alt="image" src="https://github.com/user-attachments/assets/17e3eed9-3c4d-415a-8bac-069104997150" />

### Cài đặt công cụ sau khi chỉnh sửa
Nếu ta cần sửa plugin `use-dtdl`, ta chỉ cần build lại use-dtdl, sau đó copy file `.jar` build được vào thư mục `use-assembly/src/main/resources/plugins`, sau đó chạy lại file `.bat` là được. Vì `use-assembly` đọc plugin lúc runtime, không
cần phải build lại `use-assembly` nữa.

Lệnh để build lại plugin `use-dtdl`:

```
mvn clean package -pl use-dtdl -am
```



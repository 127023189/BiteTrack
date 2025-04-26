package com.sky.controller.admin;

import com.sky.constant.MessageConstant;
import com.sky.result.Result;
import com.sky.utils.AliOssUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@Slf4j
@Api(tags = "通用接口")
@RequestMapping("/admin/common")
public class CommonController {

    @Autowired
    private AliOssUtil aliOssUtil;


    @ApiOperation("文件上传")
    @RequestMapping("/upload")
    public Result<String> upload(MultipartFile file){
        log.info("文件上传：{}", file);

        try {
            String originalFilename = file.getOriginalFilename();

            // 获取文件后缀
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));

            // 获取当前时间
            LocalDate now = LocalDate.now();
            // 构造新文件名称
            String objectName = now.toString() + "/" + UUID.randomUUID().toString() + extension;

            String filePath = aliOssUtil.upload(file.getBytes(), objectName);

            // 将上传文件URL存redis

            return Result.success(filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Result.error(MessageConstant.UPLOAD_FAILED);
    }
}

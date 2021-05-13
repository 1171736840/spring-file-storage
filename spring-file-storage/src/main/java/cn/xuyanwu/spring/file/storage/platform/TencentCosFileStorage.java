package cn.xuyanwu.spring.file.storage.platform;

import cn.xuyanwu.spring.file.storage.FileInfo;
import cn.xuyanwu.spring.file.storage.UploadPretreatment;
import cn.xuyanwu.spring.file.storage.exception.FileStorageRuntimeException;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.region.Region;
import lombok.Getter;
import lombok.Setter;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * 腾讯云 COS 存储
 */
@Getter
@Setter
public class TencentCosFileStorage implements FileStorage {

    /* 存储平台 */
    private String platform;
    private String secretId;
    private String secretKey;
    private String region;
    private String bucketName;
    private String domain;
    private String basePath;

    public COSClient getCos() {
        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        clientConfig.setHttpProtocol(HttpProtocol.https);
        return new COSClient(cred, clientConfig);
    }

    /**
     * 关闭
     */
    public void shutdown(COSClient cos) {
        if (cos != null) cos.shutdown();
    }

    @Override
    public boolean save(FileInfo fileInfo, UploadPretreatment pre) {
        String newFileKey = basePath + fileInfo.getPath() + fileInfo.getFilename();
        fileInfo.setBasePath(basePath);
        fileInfo.setUrl(domain + newFileKey);

        COSClient cos = getCos();
        try {
            cos.putObject(bucketName, newFileKey, pre.getFileWrapper().getInputStream(), null);

            byte[] thumbnailBytes = pre.getThumbnailBytes();
            if (thumbnailBytes != null) { //上传缩略图
                fileInfo.setThUrl(fileInfo.getUrl() + pre.getThumbnailSuffix());
                cos.putObject(bucketName, newFileKey + pre.getThumbnailSuffix(), new ByteArrayInputStream(thumbnailBytes), null);
            }

            return true;
        } catch (IOException e) {
            cos.deleteObject(bucketName, newFileKey);
            throw new FileStorageRuntimeException("文件上传失败！platform：" + platform + "，filename：" + fileInfo.getOriginalFilename(), e);
        } finally {
            shutdown(cos);
        }
    }

    @Override
    public boolean delete(FileInfo fileInfo) {
        COSClient cos = getCos();
        if (fileInfo.getThFilename() != null) {   //删除缩略图
            cos.deleteObject(bucketName, fileInfo.getBasePath() + fileInfo.getPath() + fileInfo.getThFilename());
        }
        cos.deleteObject(bucketName, fileInfo.getBasePath() + fileInfo.getPath() + fileInfo.getFilename());
        shutdown(cos);
        return true;
    }


    @Override
    public boolean exists(FileInfo fileInfo) {
        COSClient cos = getCos();
        boolean b = cos.doesObjectExist(bucketName, fileInfo.getBasePath() + fileInfo.getPath() + fileInfo.getFilename());
        shutdown(cos);
        return b;
    }
}
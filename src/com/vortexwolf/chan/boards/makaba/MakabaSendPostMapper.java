package com.vortexwolf.chan.boards.makaba;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map.Entry;

import org.apache.http.HttpEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;

import com.vortexwolf.chan.common.Constants;
import com.vortexwolf.chan.common.utils.StringUtils;
import com.vortexwolf.chan.models.domain.SendPostModel;

public class MakabaSendPostMapper {
    private static final Charset utf = Constants.UTF8_CHARSET;
    private static final String BOARD = "board";
    private static final String THREAD = "thread";
    private static final String USERCODE = "usercode";
    private static final String COMMENT = "comment";
    private static final String EMAIL = "email";
    private static final String NAME = "name";
    private static final String SUBJECT = "subject";
    private static final String CAPTCHA_KEY = "captcha";
    private static final String CAPTCHA_ANSWER = "captcha_value_id_06";
    private static final String FILE = "image1"; 
    
    public HttpEntity mapModelToHttpEntity(String boardName, String userCode, SendPostModel model, HashMap<String, String> customValues) {       
        MultipartEntity multipartEntity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);
        
        if (customValues != null) {
            for (Entry<String, String> entry : customValues.entrySet()) {
                this.addStringValue(multipartEntity, entry.getKey(), entry.getValue());
            }
        }

        this.addStringValue(multipartEntity, BOARD, boardName);
        this.addStringValue(multipartEntity, THREAD, model.getParentThread());
        this.addStringValue(multipartEntity, USERCODE, userCode);
        this.addStringValue(multipartEntity, COMMENT, StringUtils.emptyIfNull(model.getComment()));
        this.addStringValue(multipartEntity, CAPTCHA_KEY, model.getCaptchaKey());
        this.addStringValue(multipartEntity, CAPTCHA_ANSWER, model.getCaptchaAnswer());
        this.addStringValue(multipartEntity, SUBJECT, model.getSubject());
        this.addStringValue(multipartEntity, NAME, model.getName());
        this.addStringValue(multipartEntity, EMAIL, model.isSage() ? Constants.SAGE_EMAIL : null);
        
        if (model.getAttachment() != null) {
            multipartEntity.addPart(FILE, new FileBody(model.getAttachment()));
        }
        
        return multipartEntity;
    }
    
    private void addStringValue(MultipartEntity entity, String key, String value) {
        try {
            if (value != null) {
                entity.addPart(key, new StringBody(value, utf));
            }
        } catch (Exception ignored) {
            // ignore
        }
    }
}
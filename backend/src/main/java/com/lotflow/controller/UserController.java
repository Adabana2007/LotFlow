package com.lotflow.controller;

import com.lotflow.dto.AvatarRegistrationDTO;
import com.lotflow.dto.MediaUploadDTO;
import com.lotflow.dto.NotificationPreferencesDTO;
import com.lotflow.dto.UpdateProfileDTO;
import com.lotflow.security.CurrentUser;
import com.lotflow.service.MediaStorageService;
import com.lotflow.service.PersonalWorkspaceService;
import com.lotflow.service.UserService;
import com.lotflow.vo.CreditVO;
import com.lotflow.vo.MediaUploadVO;
import com.lotflow.vo.TodoItemVO;
import com.lotflow.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** 当前用户自助接口；所有读写都绑定安全上下文中的用户。 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final MediaStorageService mediaStorageService;
    private final PersonalWorkspaceService personalWorkspaceService;
    private final CurrentUser currentUser;

    @GetMapping("/me")
    public UserVO me() {
        // “me” 固定使用安全上下文中的 userId，客户端不能通过参数读取其他用户资料。
        return userService.getCurrentUser(currentUser.id());
    }

    @PatchMapping("/me/chat-setting")
    public ResponseEntity<Void> updateChatSetting(@RequestParam boolean enabled) {
        // 账号级聊天开关是商品和订单会话的兜底默认值，关闭后只阻止发起新会话。
        userService.updateChatEnabled(currentUser.id(), enabled);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/me")
    public ResponseEntity<Void> updateProfile(@Valid @RequestBody UpdateProfileDTO request) {
        userService.updateProfile(currentUser.id(), request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/me/notifications")
    public ResponseEntity<Void> updateNotifications(
            @Valid @RequestBody NotificationPreferencesDTO request) {
        userService.updateNotifications(currentUser.id(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/avatar-uploads")
    public MediaUploadVO avatarUpload(@Valid @RequestBody MediaUploadDTO request) {
        return mediaStorageService.createAvatarUpload(currentUser.id(), request);
    }

    @PutMapping("/me/avatar")
    public ResponseEntity<Void> updateAvatar(
            @Valid @RequestBody AvatarRegistrationDTO request) {
        String objectKey = mediaStorageService.validateAvatar(currentUser.id(), request);
        userService.updateAvatar(currentUser.id(), objectKey);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/credit")
    public CreditVO credit() {
        // 信用记录只返回当前用户最近变化，完整审计查询保留在管理员接口。
        return userService.getCredit(currentUser.id());
    }

    @GetMapping("/me/todos")
    public List<TodoItemVO> todos() {
        return personalWorkspaceService.todos(currentUser.id());
    }
}

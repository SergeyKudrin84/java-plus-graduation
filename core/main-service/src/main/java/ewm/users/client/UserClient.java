package ewm.users.client;

import ewm.users.client.dto.UserResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(
        name = "user-service",
        path = "/admin/users",
        fallbackFactory = UserClientFallbackFactory.class
)
public interface UserClient {

    @GetMapping
    List<UserResponseDto> getUsersByIds(
            @RequestParam("ids") List<Long> ids
    );
}
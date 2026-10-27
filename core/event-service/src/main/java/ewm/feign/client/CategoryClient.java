package ewm.feign.client;

import ewm.feign.client.dto.CategoryDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "main-service",
        contextId = "categoryClient",
        path = "/categories"
)
public interface CategoryClient {

    @GetMapping("/{catId}")
    CategoryDto getCategory(@PathVariable("catId") Long categoryId);
}
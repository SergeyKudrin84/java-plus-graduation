package ewm.users.mapper;

import ewm.users.client.dto.UserResponseDto;
import org.mapstruct.Mapper;
import ewm.users.client.dto.UserShortDto;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserShortDto toUserShortDto(UserResponseDto userResponseDto);

}
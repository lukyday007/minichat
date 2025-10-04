package com.dy.minichat.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class InviteRequestDTO {
    private List<Long> userIds;
}
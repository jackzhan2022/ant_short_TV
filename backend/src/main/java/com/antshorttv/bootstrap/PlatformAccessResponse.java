package com.antshorttv.bootstrap;

import java.util.List;

public record PlatformAccessResponse(List<String> roles, List<String> permissions) {
}

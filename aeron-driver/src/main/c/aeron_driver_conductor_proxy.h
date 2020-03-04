/*
 * Copyright 2014-2020 Real Logic Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef AERON_DRIVER_CONDUCTOR_PROXY_H
#define AERON_DRIVER_CONDUCTOR_PROXY_H

#include <media/aeron_send_channel_endpoint.h>
#include "aeron_driver_context.h"

typedef struct aeron_driver_conductor_stct aeron_driver_conductor_t;

typedef struct aeron_driver_conductor_proxy_stct
{
    aeron_driver_conductor_t *conductor;
    aeron_threading_mode_t threading_mode;
    aeron_mpsc_concurrent_array_queue_t *command_queue;
    int64_t *fail_counter;
}
aeron_driver_conductor_proxy_t;

void aeron_driver_conductor_proxy_on_delete_cmd(
    aeron_driver_conductor_proxy_t *conductor_proxy, aeron_command_base_t *cmd);

typedef struct aeron_command_create_publication_image_stct
{
    aeron_command_base_t base;
    int32_t session_id;
    int32_t stream_id;
    int32_t initial_term_id;
    int32_t active_term_id;
    int32_t term_offset;
    int32_t term_length;
    int32_t mtu_length;
    struct sockaddr_storage control_address;
    struct sockaddr_storage src_address;
    void *endpoint;
}
aeron_command_create_publication_image_t;

typedef struct aeron_command_re_resolve_endpoint_stct
{
    aeron_command_base_t base;
    const char *endpoint_name;
    void *endpoint;
    struct sockaddr_storage existing_addr;
}
aeron_command_re_resolve_endpoint_t;

void aeron_driver_conductor_proxy_on_create_publication_image_cmd(
    aeron_driver_conductor_proxy_t *conductor_proxy,
    int32_t session_id,
    int32_t stream_id,
    int32_t initial_term_id,
    int32_t active_term_id,
    int32_t term_offset,
    int32_t term_length,
    int32_t mtu_length,
    struct sockaddr_storage *control_address,
    struct sockaddr_storage *src_address,
    void *endpoint);

void aeron_driver_conductor_proxy_on_linger_buffer(aeron_driver_conductor_proxy_t *conductor_proxy, uint8_t *buffer);

void aeron_driver_conductor_proxy_on_re_resolve_endpoint(
    aeron_driver_conductor_proxy_t *conductor_proxy,
    const char *endpoint_name,
    aeron_send_channel_endpoint_t *endpoint,
    struct sockaddr_storage *existing_addr);

#endif //AERON_DRIVER_CONDUCTOR_PROXY_H

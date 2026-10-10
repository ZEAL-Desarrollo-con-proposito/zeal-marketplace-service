# zeal-marketplace-service

## Chat STOMP legado

El chat usa WebSocket con STOMP, autenticado con el mismo JWT del servicio. El endpoint
`/ws/chat` acepta el token como parámetro `token` (para clientes de navegador) o en el
encabezado `Authorization`. Solo permite los roles `EMPLOYEE` y `PSYCHOLOGIST`.

- Suscripción a mensajes privados: `/user/queue/messages`
- Suscripción a errores de envío: `/user/queue/errors`
- Envío de mensaje STOMP: `/app/chat.send`
- Cuerpo de envío: `{"recipientId":"<id>","content":"<mensaje>"}`
- Mensaje recibido: `senderId`, `senderRole`, `recipientId`, `content` y `sentAt`

El destinatario debe estar conectado y tener el rol contrario al emisor. Si está
desconectado, el emisor recibe un error por `/user/queue/errors`; este endpoint legado no conserva
historial. Cada sesión STOMP se registra y elimina al desconectarse, permitiendo que
un usuario se reconecte sin mantener presencia obsoleta. El endpoint permite origen
`http://localhost:3000`, igual que la configuración CORS existente.

## Chat persistente usado por el cliente Zeal

El cliente se conecta al Real-Time Gateway en `/ws`, envía a
`/app/chats/{conversationId}/mensajes` un cuerpo `{"text":"..."}` y recibe eventos
privados `{"type":"NUEVO","message":...}` en `/user/queue/chats`. La conversación se
identifica como `{employeeId}:{psychologistId}`. El gateway valida el JWT, solicita al
servicio que persista el mensaje y solo entonces lo entrega al emisor y al otro
participante; por eso los mensajes enviados mientras alguien está desconectado quedan
disponibles al recuperar el historial.

El historial se consulta con `GET /api/chats/{conversationId}/mensajes`. Solo el
empleado o el psicólogo que forma parte de la conversación puede leerlo. Las entidades
`Conversation` y `PersistedChatMessage` se guardan en PostgreSQL del Marketplace.
Configura `MARKETPLACE_BASE_URL` en el Real-Time Gateway si el servicio no está en
`http://localhost:8082`.

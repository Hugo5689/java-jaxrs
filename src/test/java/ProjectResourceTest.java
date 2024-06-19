import factories.*;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Persistence;
import jakarta.ws.rs.*;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import org.faya.sensei.entities.*;
import org.faya.sensei.payloads.ProjectDTO;
import org.faya.sensei.payloads.TaskDTO;
import org.faya.sensei.repositories.IRepository;
import org.faya.sensei.resources.endpoints.ProjectResource;
import org.faya.sensei.resources.endpoints.TaskResource;
import org.faya.sensei.services.IService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reflections.Reflections;
import wrappers.ProjectEntityWrapper;
import wrappers.StatusEntityWrapper;
import wrappers.TaskEntityWrapper;
import wrappers.UserEntityWrapper;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ProjectResourceTest {

    @Test
    public void testAnnotations() {
        assertAll(
                () -> {
                    final String projectResourceClassName = ProjectResource.class.getSimpleName();

                    final Optional<Method> getMethod = Arrays.stream(ProjectResource.class.getDeclaredMethods())
                            .filter(method -> method.isAnnotationPresent(GET.class))
                            .findFirst();

                    assertTrue(
                            getMethod.isPresent(),
                            "One method under %s claas should be annotated with @GET".formatted(projectResourceClassName)
                    );

                    final Optional<Method> postMethod = Arrays.stream(ProjectResource.class.getDeclaredMethods())
                            .filter(method -> method.isAnnotationPresent(POST.class))
                            .findFirst();

                    assertTrue(
                            postMethod.isPresent(),
                            "One method under %s claas should be annotated with @POST".formatted(projectResourceClassName)
                    );
                },
                () -> {
                    final String taskResourceClassName = TaskResource.class.getSimpleName();

                    final Optional<Method> postMethod = Arrays.stream(TaskResource.class.getDeclaredMethods())
                            .filter(method -> method.isAnnotationPresent(POST.class))
                            .findFirst();

                    assertTrue(
                            postMethod.isPresent(),
                            "One method under %s claas should be annotated with @POST".formatted(taskResourceClassName)
                    );

                    final Optional<Method> putMethod = Arrays.stream(TaskResource.class.getDeclaredMethods())
                            .filter(method -> method.isAnnotationPresent(PUT.class))
                            .findFirst();

                    assertTrue(
                            putMethod.isPresent(),
                            "One method under %s claas should be annotated with @PUT".formatted(taskResourceClassName)
                    );

                    final Optional<Method> deleteMethod = Arrays.stream(TaskResource.class.getDeclaredMethods())
                            .filter(method -> method.isAnnotationPresent(DELETE.class))
                            .findFirst();

                    assertTrue(
                            deleteMethod.isPresent(),
                            "One method under %s claas should be annotated with @DELETE".formatted(taskResourceClassName)
                    );
                }
        );
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    public class UnitTest {

        @Nested
        @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
        public class ProjectRepositoryTest {

            private static final EntityManagerFactory entityManagerFactory = Persistence.createEntityManagerFactory("java-jaxrs-database");

            private static IRepository<ProjectEntity> projectRepository;

            private static ProjectEntityWrapper projectEntity;

            private static UserEntityWrapper userEntity;

            private static int targetId = -1;

            @BeforeAll
            @SuppressWarnings("unchecked")
            public static void setUp() throws Exception {
                final Reflections reflections = new Reflections("org.faya.sensei.repositories");
                final Set<Class<? extends IRepository>> classes = reflections.getSubTypesOf(IRepository.class);

                final Class<? extends IRepository> projectRepositoryClass = classes.stream()
                        .filter(cls -> {
                            final Type[] genericInterfaces = cls.getGenericInterfaces();
                            for (final Type genericInterface : genericInterfaces) {
                                if (genericInterface instanceof ParameterizedType parameterizedType) {
                                    final Type[] typeArguments = parameterizedType.getActualTypeArguments();
                                    if (typeArguments.length == 1 && typeArguments[0].equals(ProjectEntity.class))
                                        return true;
                                }
                            }

                            return false;
                        })
                        .findFirst()
                        .orElseThrow(() ->
                                new ClassNotFoundException("No implementation found for IRepository<ProjectEntity>"));

                projectRepository = projectRepositoryClass.getDeclaredConstructor().newInstance();
                userEntity = UserFactory.createUserEntity("user", "password", UserRole.ADMIN).build();
                projectEntity = ProjectFactory.createProjectEntity("project", List.of(userEntity.entity())).build();

                final Field userRepositoryField = projectRepositoryClass.getDeclaredField("entityManager");
                userRepositoryField.setAccessible(true);
                userRepositoryField.set(projectRepository, entityManagerFactory.createEntityManager());
            }

            @Test
            @Order(1)
            public void testPost() {
                targetId = projectRepository.post(projectEntity.entity());

                assertTrue(targetId > 0);
            }

            @Test
            @Order(2)
            public void testGetById() {
                final Optional<ProjectEntity> actualProject = projectRepository.get(targetId);

                assertTrue(actualProject.isPresent());
                assertEquals(projectEntity.entity().getName(), actualProject.get().getName());
            }

            @Test
            @Order(3)
            public void testGetByUser() {
                final Collection<ProjectEntity> actualProjects = projectRepository.getBy("users.name", "user");

                assertFalse(actualProjects.isEmpty());
                assertArrayEquals(actualProjects.toArray(), List.of(projectEntity.entity()).toArray());
            }

            @Test
            @Order(4)
            public void testPut() {
                projectEntity = ProjectFactory.createProjectEntity("updated project", List.of(userEntity.entity())).build();

                final Optional<ProjectEntity> actualProject = projectRepository.put(targetId, projectEntity.entity());

                assertTrue(actualProject.isPresent());
                assertEquals(projectEntity.entity().getName(), actualProject.get().getName());
            }

            @Test
            @Order(5)
            public void testDelete() {
                final Optional<ProjectEntity> actualProject = projectRepository.delete(targetId);

                assertTrue(actualProject.isPresent());
                assertEquals(projectEntity.entity().getName(), actualProject.get().getName());
            }
        }

        @Nested
        @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
        public class ProjectServiceTest {

            @Mock
            private IRepository<ProjectEntity> projectRepository;

            private IService<ProjectDTO> projectService;

            @BeforeEach
            @SuppressWarnings("unchecked")
            public void prepare() throws Exception {
                final Reflections reflections = new Reflections("org.faya.sensei.services");
                final Set<Class<? extends IService>> classes = reflections.getSubTypesOf(IService.class);

                final Class<? extends IService> projectServiceClass = classes.stream()
                        .filter(cls -> {
                            final Type[] genericInterfaces = cls.getGenericInterfaces();
                            for (final Type genericInterface : genericInterfaces) {
                                if (genericInterface instanceof ParameterizedType parameterizedType) {
                                    final Type[] typeArguments = parameterizedType.getActualTypeArguments();
                                    if (typeArguments.length == 1 && typeArguments[0].equals(ProjectDTO.class))
                                        return true;
                                }
                            }

                            return false;
                        })
                        .findFirst()
                        .orElseThrow(() ->
                                new ClassNotFoundException("No implementation found for IService<ProjectDTO>"));

                projectService = projectServiceClass.getDeclaredConstructor().newInstance();

                final Field projectRepositoryField = projectServiceClass.getDeclaredField("projectRepository");
                projectRepositoryField.setAccessible(true);
                projectRepositoryField.set(projectService, projectRepository);
            }

            @Test
            @Order(1)
            public void testCreate() {
                final ProjectDTO projectDTO = ProjectFactory.createProjectDTO("project").toDTO();

                when(projectRepository.post(any(ProjectEntity.class))).thenReturn(1);

                final Optional<ProjectDTO> actualProjectDTO = projectService.create(projectDTO);

                verify(projectRepository, times(1)).post(any(ProjectEntity.class));
                assertTrue(actualProjectDTO.isPresent());
            }

            @Test
            @Order(2)
            public void testGet() {
                final int projectId = 1;

                final ProjectEntity projectEntity = ProjectFactory.createProjectEntity(projectId, "project", List.of()).toEntity();

                when(projectRepository.get(projectId)).thenReturn(Optional.of(projectEntity));

                final Optional<ProjectDTO> actualProjectDTO = projectService.get(projectId);

                verify(projectRepository, times(1)).get(projectId);
                assertTrue(actualProjectDTO.isPresent());
            }

            @Test
            @Order(4)
            public void testUpdate() {
                final int projectId = 1;

                final ProjectDTO projectDTO = ProjectFactory.createProjectDTO("updated project").toDTO();
                final ProjectEntity projectEntity = ProjectFactory.createProjectEntity(projectId, "updated project", List.of()).toEntity();

                when(projectRepository.put(eq(projectId), any(ProjectEntity.class))).thenReturn(Optional.of(projectEntity));

                final Optional<ProjectDTO> actualProjectDTO = projectService.update(projectId, projectDTO);

                verify(projectRepository, times(1)).put(eq(projectId), any(ProjectEntity.class));
                assertTrue(actualProjectDTO.isPresent());
                assertEquals(projectEntity.getName(), actualProjectDTO.get().getName());
            }

            @Test
            @Order(5)
            public void testRemove() {
                final int projectId = 1;

                final ProjectEntity projectEntity = ProjectFactory.createProjectEntity(projectId, "project", List.of()).toEntity();

                when(projectRepository.delete(projectId)).thenReturn(Optional.of(projectEntity));

                final boolean actualResult = projectService.remove(projectId);

                verify(projectRepository, times(1)).delete(projectId);
                assertTrue(actualResult);
            }
        }

        @Nested
        @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
        public class TaskRepositoryTest {

            private static final EntityManagerFactory entityManagerFactory = Persistence.createEntityManagerFactory("java-jaxrs-database");

            private static IRepository<TaskEntity> taskRepository;

            private static ProjectEntityWrapper projectEntity;

            private static UserEntityWrapper userEntity;

            private static StatusEntityWrapper statusEntity;

            private static TaskEntityWrapper taskEntity;

            private static int targetId = -1;

            @BeforeAll
            @SuppressWarnings("unchecked")
            public static void setUp() throws Exception {
                final Reflections reflections = new Reflections("org.faya.sensei.repositories");
                final Set<Class<? extends IRepository>> classes = reflections.getSubTypesOf(IRepository.class);

                final Class<? extends IRepository> taskRepositoryClass = classes.stream()
                        .filter(cls -> {
                            final Type[] genericInterfaces = cls.getGenericInterfaces();
                            for (final Type genericInterface : genericInterfaces) {
                                if (genericInterface instanceof ParameterizedType parameterizedType) {
                                    final Type[] typeArguments = parameterizedType.getActualTypeArguments();
                                    if (typeArguments.length == 1 && typeArguments[0].equals(TaskEntity.class))
                                        return true;
                                }
                            }

                            return false;
                        })
                        .findFirst()
                        .orElseThrow(() ->
                                new ClassNotFoundException("No implementation found for IRepository<TaskEntity>"));

                taskRepository = taskRepositoryClass.getDeclaredConstructor().newInstance();
                userEntity = UserFactory.createUserEntity("user", "password", UserRole.ADMIN).build();
                projectEntity = ProjectFactory.createProjectEntity("project", List.of(userEntity.entity())).build();
                statusEntity = StatusFactory.createStatusEntity("todo", projectEntity.entity()).build();
                taskEntity = TaskFactory.createTaskEntity()
                        .setTitle("task")
                        .setDescription("task description")
                        .setEndDate(LocalDateTime.now().plusMinutes(10))
                        .setStatus(statusEntity.entity())
                        .setProject(projectEntity.entity())
                        .setAssigner(userEntity.entity())
                        .build();

                final Field userRepositoryField = taskRepositoryClass.getDeclaredField("entityManager");
                userRepositoryField.setAccessible(true);
                userRepositoryField.set(taskRepository, entityManagerFactory.createEntityManager());
            }

            @Test
            @Order(1)
            public void testPost() {
                targetId = taskRepository.post(taskEntity.entity());

                assertTrue(targetId > 0);
            }

            @Test
            @Order(2)
            public void testGetById() {
                final Optional<TaskEntity> actualTask = taskRepository.get(targetId);

                assertTrue(actualTask.isPresent());
                assertEquals(taskEntity.entity().getTitle(), actualTask.get().getTitle());
                assertEquals(taskEntity.entity().getDescription(), actualTask.get().getDescription());
                assertEquals(taskEntity.entity().getStatus(), actualTask.get().getStatus());
            }

            @Test
            @Order(4)
            public void testPut() {
                taskEntity = TaskFactory.createTaskEntity().setTitle("updated task").build();

                final Optional<TaskEntity> actualTask = taskRepository.put(targetId, taskEntity.entity());

                assertTrue(actualTask.isPresent());
                assertEquals(taskEntity.entity().getTitle(), actualTask.get().getTitle());
            }

            @Test
            @Order(5)
            public void testDelete() {
                final Optional<TaskEntity> actualTask = taskRepository.delete(targetId);

                assertTrue(actualTask.isPresent());
                assertEquals(taskEntity.entity().getTitle(), actualTask.get().getTitle());
            }
        }

        @Nested
        @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
        public class TaskServiceTest {

            @Mock
            private IRepository<UserEntity> userRepository;

            @Mock
            private IRepository<ProjectEntity> projectRepository;

            @Mock
            private IRepository<StatusEntity> statusRepository;

            @Mock
            private IRepository<TaskEntity> taskRepository;

            private IService<TaskDTO> taskService;

            @BeforeEach
            @SuppressWarnings("unchecked")
            public void prepare() throws Exception {
                final Reflections reflections = new Reflections("org.faya.sensei.services");
                final Set<Class<? extends IService>> classes = reflections.getSubTypesOf(IService.class);

                final Class<? extends IService> taskServiceClass = classes.stream()
                        .filter(cls -> {
                            final Type[] genericInterfaces = cls.getGenericInterfaces();
                            for (final Type genericInterface : genericInterfaces) {
                                if (genericInterface instanceof ParameterizedType parameterizedType) {
                                    final Type[] typeArguments = parameterizedType.getActualTypeArguments();
                                    if (typeArguments.length == 1 && typeArguments[0].equals(TaskDTO.class))
                                        return true;
                                }
                            }

                            return false;
                        })
                        .findFirst()
                        .orElseThrow(() ->
                                new ClassNotFoundException("No implementation found for IService<TaskDTO>"));

                taskService = taskServiceClass.getDeclaredConstructor().newInstance();

                final Field userRepositoryField = taskServiceClass.getDeclaredField("userRepository");
                userRepositoryField.setAccessible(true);
                userRepositoryField.set(taskService, userRepository);

                final Field projectRepositoryField = taskServiceClass.getDeclaredField("projectRepository");
                projectRepositoryField.setAccessible(true);
                projectRepositoryField.set(taskService, projectRepository);

                final Field statusRepositoryField = taskServiceClass.getDeclaredField("statusRepository");
                statusRepositoryField.setAccessible(true);
                statusRepositoryField.set(taskService, statusRepository);

                final Field taskRepositoryField = taskServiceClass.getDeclaredField("taskRepository");
                taskRepositoryField.setAccessible(true);
                taskRepositoryField.set(taskService, taskRepository);
            }

            @Test
            @Order(1)
            public void testCreate() {
                final UserEntity userEntity = UserFactory.createUserEntity(1, "user", "password", UserRole.USER).toEntity();
                final ProjectEntity projectEntity = ProjectFactory.createProjectEntity(1, "project", List.of(userEntity)).toEntity();
                final StatusEntity statusEntity = StatusFactory.createStatusEntity(1, "todo", projectEntity).toEntity();

                final TaskDTO taskDTO = TaskFactory.createTaskDTO()
                        .setTitle("task")
                        .setDescription("task description")
                        .setEndDate(LocalDateTime.now().plusMinutes(10))
                        .setStatus("todo")
                        .setProjectId(1)
                        .setAssignerId(1)
                        .toDTO();

                when(projectRepository.get(taskDTO.getProjectId())).thenReturn(Optional.of(projectEntity));
                when(userRepository.get(taskDTO.getAssignerId())).thenReturn(Optional.of(userEntity));
                when(statusRepository.get(taskDTO.getStatus())).thenReturn(Optional.of(statusEntity));
                when(taskRepository.post(any(TaskEntity.class))).thenReturn(1);

                final Optional<TaskDTO> actualTaskDTO = taskService.create(taskDTO);

                verify(projectRepository, times(1)).get(taskDTO.getProjectId());
                verify(statusRepository, times(1)).get(taskDTO.getStatus());
                verify(userRepository, times(1)).get(taskDTO.getAssignerId());
                verify(taskRepository, times(1)).post(any(TaskEntity.class));
                assertTrue(actualTaskDTO.isPresent());
            }

            @Test
            @Order(2)
            public void testUpdate() {
                final int taskId = 1;

                final UserEntity userEntity = UserFactory.createUserEntity(2, "sensei", "password", UserRole.USER).toEntity();
                final ProjectEntity projectEntity = ProjectFactory.createProjectEntity(1, "project", List.of(userEntity)).toEntity();
                final StatusEntity statusEntity = StatusFactory.createStatusEntity(2, "done", projectEntity).toEntity();
                final TaskEntity taskEntity = TaskFactory.createTaskEntity()
                        .setId(1)
                        .setTitle("new task")
                        .setDescription("task description")
                        .setStartDate(LocalDateTime.now())
                        .setEndDate(LocalDateTime.now().plusMinutes(10))
                        .setStatus(statusEntity)
                        .setProject(projectEntity)
                        .setAssigner(userEntity)
                        .toEntity();

                final TaskDTO taskDTO = TaskFactory.createTaskDTO()
                        .setTitle("new task")
                        .setStatus("done")
                        .setAssignerId(2)
                        .toDTO();

                when(userRepository.get(taskDTO.getAssignerId())).thenReturn(Optional.of(userEntity));
                when(statusRepository.get(taskDTO.getStatus())).thenReturn(Optional.of(statusEntity));
                when(taskRepository.put(eq(taskId), any(TaskEntity.class))).thenReturn(Optional.of(taskEntity));

                final Optional<TaskDTO> actualTaskDTO = taskService.update(taskId, taskDTO);

                verify(userRepository, times(1)).get(taskDTO.getAssignerId());
                verify(statusRepository, times(1)).get(taskDTO.getStatus());
                verify(taskRepository, times(1)).put(eq(taskId), any(TaskEntity.class));
                assertTrue(actualTaskDTO.isPresent());
            }

            @Test
            @Order(3)
            public void testRemove() {
                final int taskId = 1;

                final UserEntity userEntity = UserFactory.createUserEntity(1, "user", "password", UserRole.USER).toEntity();
                final ProjectEntity projectEntity = ProjectFactory.createProjectEntity(1, "project", List.of(userEntity)).toEntity();
                final StatusEntity statusEntity = StatusFactory.createStatusEntity(1, "todo", projectEntity).toEntity();
                final TaskEntity taskEntity = TaskFactory.createTaskEntity()
                        .setId(1)
                        .setTitle("task")
                        .setDescription("task description")
                        .setStartDate(LocalDateTime.now())
                        .setEndDate(LocalDateTime.now().plusMinutes(10))
                        .setStatus(statusEntity)
                        .setProject(projectEntity)
                        .setAssigner(userEntity)
                        .toEntity();

                when(taskRepository.delete(taskId)).thenReturn(Optional.of(taskEntity));

                final boolean actualResult = taskService.remove(taskId);

                verify(taskRepository, times(1)).delete(taskId);
                assertTrue(actualResult);
            }
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    public class IntegrationTest {

        private static final EntityManagerFactory entityManagerFactory = Persistence.createEntityManagerFactory("java-jaxrs-database");

        private static final SeBootstrap.Instance instance = ServerFactory.createServer(entityManagerFactory);

        private static final URI uri = instance.configuration().baseUri();

        private static List<ProjectEntityWrapper> projectEntities;

        private static List<StatusEntityWrapper> StatusEntities;

        private static List<TaskEntityWrapper> taskEntities;

        private static String cacheToken;

        @BeforeAll
        public static void setUp() {
            final JsonObject registerUserBody = Json.createObjectBuilder(Map.of("name", "user", "password", "password")).build();

            try (final Client client = ClientBuilder.newClient()) {
                final WebTarget target = client.target(UriBuilder.fromUri(uri).path("/api/auth/register").build());

                try (final Response response = target.request(MediaType.APPLICATION_JSON)
                        .post(Entity.entity(registerUserBody, MediaType.APPLICATION_JSON))) {
                    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

                    try (final JsonReader jsonReader = Json.createReader((InputStream) response.getEntity())) {
                        final EntityManager entityManager = entityManagerFactory.createEntityManager();
                        final EntityTransaction transaction = entityManager.getTransaction();
                        final JsonObject jsonObject = jsonReader.readObject();

                        transaction.begin();

                        cacheToken = jsonObject.getJsonString("token").getString();

                        final UserEntityWrapper userEntity = new UserEntityWrapper(entityManager.find(UserEntity.class, jsonObject.getInt("id")));

                        projectEntities = List.of(
                                ProjectFactory.createProjectEntity("project", List.of(userEntity.entity())).build()
                        );
                        StatusEntities = List.of(
                                StatusFactory.createStatusEntity("todo", projectEntities.getFirst().entity()).build(),
                                StatusFactory.createStatusEntity("done", projectEntities.getFirst().entity()).build()
                        );
                        taskEntities = List.of(
                                TaskFactory.createTaskEntity()
                                        .setTitle("test task 1")
                                        .setDescription("task 1 test description.")
                                        .setStartDate(LocalDateTime.now())
                                        .setEndDate(LocalDateTime.now().plusMinutes(10))
                                        .setStatus(StatusEntities.getFirst().entity())
                                        .setProject(projectEntities.getFirst().entity())
                                        .setAssigner(userEntity.entity())
                                        .build(),
                                TaskFactory.createTaskEntity()
                                        .setTitle("test task 2")
                                        .setDescription("task 2 test description.")
                                        .setStartDate(LocalDateTime.now())
                                        .setEndDate(LocalDateTime.now().plusMinutes(20))
                                        .setStatus(StatusEntities.getLast().entity())
                                        .setProject(projectEntities.getFirst().entity())
                                        .setAssigner(userEntity.entity())
                                        .build()
                        );

                        projectEntities.forEach(project -> entityManager.persist(project.entity()));
                        StatusEntities.forEach(status -> entityManager.persist(status.entity()));
                        taskEntities.forEach(task -> entityManager.persist(task.entity()));

                        transaction.commit();

                        entityManager.close();
                    }
                }
            }
        }

        @Test
        @Order(1)
        public void testSaveProject() {
            final JsonObject creationTaskBody = Json.createObjectBuilder()
                    .add("name", "new project")
                    .build();

            try (final Client client = ClientBuilder.newClient()) {
                final WebTarget target = client.target(UriBuilder.fromUri(uri).path("/api/project").build());

                try (final Response response = target.request(MediaType.APPLICATION_JSON)
                        .header("Authorization", String.format("Bearer %s", cacheToken))
                        .post(Entity.entity(creationTaskBody, MediaType.APPLICATION_JSON))) {
                    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

                    try (final JsonReader jsonReader = Json.createReader((InputStream) response.getEntity())) {
                        final JsonObject actualJsonObject = jsonReader.readObject();

                        final String actualName = actualJsonObject.getJsonString("name").getString();

                        assertEquals(creationTaskBody.getJsonString("name").getString(), actualName);
                    }
                }
            }
        }

        @Test
        @Order(2)
        public void testGetAllProjects() {
            try (final Client client = ClientBuilder.newClient()) {
                final WebTarget target = client.target(UriBuilder.fromUri(uri).path("/api/project").build());

                try (final Response response = target.request(MediaType.APPLICATION_JSON)
                        .header("Authorization", String.format("Bearer %s", cacheToken))
                        .get()) {
                    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

                    try (final JsonReader jsonReader = Json.createReader((InputStream) response.getEntity())) {
                        final JsonObject actualJsonObject = jsonReader.readArray().getFirst().asJsonObject();

                        final String actualName = actualJsonObject.getJsonString("name").getString();

                        assertEquals(projectEntities.getFirst().getName(), actualName);
                    }
                }
            }
        }

        @Test
        @Order(3)
        public void testGetProject() {
            final ProjectEntityWrapper targetProjectEntity = projectEntities.getFirst();

            try (final Client client = ClientBuilder.newClient()) {
                final String path = "/api/project/%d".formatted(targetProjectEntity.getId());
                final WebTarget target = client.target(UriBuilder.fromUri(uri).path(path).build());

                try (final Response response = target.request(MediaType.APPLICATION_JSON)
                        .header("Authorization", String.format("Bearer %s", cacheToken))
                        .get()) {
                    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

                    try (final JsonReader jsonReader = Json.createReader((InputStream) response.getEntity())) {
                        final JsonObject actualJsonObject = jsonReader.readObject();

                        final String actualName = actualJsonObject.getJsonString("name").getString();

                        assertEquals(projectEntities.getFirst().getName(), actualName);

                        final JsonArray actualTaskJsonArray = actualJsonObject.getJsonArray("tasks");

                        for (int i = 0; i < taskEntities.size(); i++) {
                            final JsonObject actualTaskJsonObject = actualTaskJsonArray.getJsonObject(i);
                            final TaskEntityWrapper expectedTask = taskEntities.get(i);

                            final String actualTitle = actualTaskJsonObject.getJsonString("title").getString();
                            final String actualDescription = actualTaskJsonObject.getJsonString("description").getString();
                            final String actualStatus = actualTaskJsonObject.getJsonString("status").getString();
                            final int actualProjectId = actualTaskJsonObject.getInt("projectId");
                            final int actualAssignerId = actualTaskJsonObject.getInt("assignerId");

                            assertEquals(expectedTask.getTitle(), actualTitle);
                            assertEquals(expectedTask.getDescription(), actualDescription);
                            assertEquals(expectedTask.getStatus().getName(), actualStatus);
                            assertEquals(expectedTask.getProject().getId(), actualProjectId);
                            assertEquals(expectedTask.getAssigner().getId(), actualAssignerId);
                        }
                    }
                }
            }
        }

        @Test
        @Order(4)
        public void testSaveTask() {
            final JsonObject creationTaskBody = Json.createObjectBuilder()
                    .add("title", "new test task")
                    .add("description", "new test task description.")
                    .add("startDate", LocalDateTime.now().toString())
                    .add("endDate", LocalDateTime.now().plusMinutes(5).toString())
                    .add("status", StatusEntities.getFirst().getName())
                    .add("projectId", 1)
                    .add("assignerId", 1)
                    .build();

            try (final Client client = ClientBuilder.newClient()) {
                final WebTarget target = client.target(UriBuilder.fromUri(uri).path("/api/project/tasks").build());

                try (final Response response = target.request(MediaType.APPLICATION_JSON)
                        .header("Authorization", String.format("Bearer %s", cacheToken))
                        .post(Entity.entity(creationTaskBody, MediaType.APPLICATION_JSON))) {
                    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

                    try (final JsonReader jsonReader = Json.createReader((InputStream) response.getEntity())) {
                        final JsonObject actualJsonObject = jsonReader.readObject();

                        final String actualTitle = actualJsonObject.getJsonString("title").getString();
                        final String actualDescription = actualJsonObject.getJsonString("description").getString();
                        final String actualStatus = actualJsonObject.getJsonString("status").getString();
                        final int actualProjectId = actualJsonObject.getInt("projectId");
                        final int actualAssignerId = actualJsonObject.getInt("assignerId");

                        assertEquals(creationTaskBody.getString("title"), actualTitle);
                        assertEquals(creationTaskBody.getString("description"), actualDescription);
                        assertEquals(creationTaskBody.getString("status"), actualStatus);
                        assertEquals(creationTaskBody.getInt("projectId"), actualProjectId);
                        assertEquals(creationTaskBody.getInt("assignerId"), actualAssignerId);
                    }
                }
            }
        }

        @Test
        @Order(5)
        public void testUpdateTask() {
            final TaskEntityWrapper targetTaskEntity = taskEntities.getFirst();
            final JsonObject updateTaskBody = Json.createObjectBuilder()
                    .add("title", "updated test task")
                    .add("endDate", LocalDateTime.now().plusMinutes(10).toString())
                    .add("status", StatusEntities.getLast().getName())
                    .build();

            try (final Client client = ClientBuilder.newClient()) {
                final String path = "/api/project/tasks/%d".formatted(targetTaskEntity.getId());
                final WebTarget target = client.target(UriBuilder.fromUri(uri).path(path).build());

                try (final Response response = target.request(MediaType.APPLICATION_JSON)
                        .header("Authorization", String.format("Bearer %s", cacheToken))
                        .put(Entity.entity(updateTaskBody, MediaType.APPLICATION_JSON))) {
                    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

                    try (final JsonReader jsonReader = Json.createReader((InputStream) response.getEntity())) {
                        final JsonObject actualJsonObject = jsonReader.readObject();

                        final String actualTitle = actualJsonObject.getJsonString("title").getString();
                        final String actualDescription = actualJsonObject.getJsonString("description").getString();
                        final String actualStatus = actualJsonObject.getJsonString("status").getString();
                        final int actualProjectId = actualJsonObject.getInt("projectId");
                        final int actualAssignerId = actualJsonObject.getInt("assignerId");

                        assertEquals(updateTaskBody.getString("title"), actualTitle);
                        assertEquals(targetTaskEntity.getDescription(), actualDescription);
                        assertEquals(updateTaskBody.getString("status"), actualStatus);
                        assertEquals(targetTaskEntity.getProject().getId(), actualProjectId);
                        assertEquals(targetTaskEntity.getAssigner().getId(), actualAssignerId);
                    }
                }
            }
        }
    }
}

package com.gjl.music.playback.subsonic;
import com.gjl.music.playback.infra.security.SubsonicAuthFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@DisplayName("SubsonicAuthFilter 单元测试")
class SubsonicAuthFilterTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain chain;
    @Mock
    private SubsonicAuthFilter.PasswordLookup passwordLookup;

    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        responseWriter = new StringWriter();
        PrintWriter pw = new PrintWriter(responseWriter);
        lenient().when(response.getWriter()).thenReturn(pw);
        SecurityContextHolder.clearContext();
    }


    @Nested
    @DisplayName("非 Subsonic 路径跳过")
    class SkipNonSubsonic {

        @Test
        @DisplayName("/api/player → 跳过并放行")
        void apiPath() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/player/stream");

            SubsonicAuthFilter filter = new SubsonicAuthFilter(passwordLookup);
            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).setStatus(401);
        }
    }


    @Nested
    @DisplayName("已有 JWT 认证跳过")
    class SkipAuthenticated {

        @Test
        @DisplayName("已认证用户直接放行")
        void alreadyAuthenticated() throws Exception {
            when(request.getRequestURI()).thenReturn("/rest/getIndexes");
            Authentication auth = mock(Authentication.class);
            when(auth.isAuthenticated()).thenReturn(true);
            SecurityContext ctx = SecurityContextHolder.getContext();
            ctx.setAuthentication(auth);

            SubsonicAuthFilter filter = new SubsonicAuthFilter(passwordLookup);
            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);
        }
    }


    @Nested
    @DisplayName("Token+Salt MD5 认证成功")
    class TokenSaltAuthSuccess {

        @Test
        @DisplayName("正确 token+salt → 设置 SecurityContext 并放行")
        void validToken() throws Exception {
            when(request.getRequestURI()).thenReturn("/rest/getAlbumList");
            when(request.getParameter("u")).thenReturn("testuser");
            when(request.getParameter("t")).thenReturn(computeToken("secret", "salt123"));
            when(request.getParameter("s")).thenReturn("salt123");
            when(passwordLookup.getPassword("testuser")).thenReturn("secret");

            SubsonicAuthFilter filter = new SubsonicAuthFilter(passwordLookup);
            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);

                        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertNotNull(auth);
            assertEquals("testuser", auth.getName());
            assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("music:browse")));
        }
    }


    @Nested
    @DisplayName("认证失败返回 401")
    class AuthFailure {

        @Test
        @DisplayName("错误 token → 401")
        void wrongToken() throws Exception {
            when(request.getRequestURI()).thenReturn("/rest/getAlbumList");
            when(request.getParameter("u")).thenReturn("testuser");
            when(request.getParameter("t")).thenReturn("wrongtoken");
            when(request.getParameter("s")).thenReturn("salt123");
            when(passwordLookup.getPassword("testuser")).thenReturn("secret");

            SubsonicAuthFilter filter = new SubsonicAuthFilter(passwordLookup);
            filter.doFilterInternal(request, response, chain);

            verify(response).setStatus(401);
            verify(chain, never()).doFilter(request, response);
            String body = responseWriter.toString();
            assertTrue(body.contains("\"failed\""));
            assertTrue(body.contains("40"));
        }

        @Test
        @DisplayName("无认证参数 → 401")
        void noAuthParams() throws Exception {
            when(request.getRequestURI()).thenReturn("/rest/getAlbumList");

            SubsonicAuthFilter filter = new SubsonicAuthFilter(passwordLookup);
            filter.doFilterInternal(request, response, chain);

            verify(response).setStatus(401);
        }

        @Test
        @DisplayName("passwordLookup 返回 null → 401")
        void nullPassword() throws Exception {
            when(request.getRequestURI()).thenReturn("/rest/getAlbumList");
            when(request.getParameter("u")).thenReturn("testuser");
            when(request.getParameter("t")).thenReturn("token");
            when(request.getParameter("s")).thenReturn("salt");
            when(passwordLookup.getPassword("testuser")).thenReturn(null);

            SubsonicAuthFilter filter = new SubsonicAuthFilter(passwordLookup);
            filter.doFilterInternal(request, response, chain);

            verify(response).setStatus(401);
        }

        @Test
        @DisplayName("passwordLookup 为 null → 401")
        void nullLookup() throws Exception {
            when(request.getRequestURI()).thenReturn("/rest/getAlbumList");
            when(request.getParameter("u")).thenReturn("testuser");
            when(request.getParameter("t")).thenReturn("token");
            when(request.getParameter("s")).thenReturn("salt");

            SubsonicAuthFilter filter = new SubsonicAuthFilter(null);
            filter.doFilterInternal(request, response, chain);

            verify(response).setStatus(401);
        }
    }


    @Nested
    @DisplayName("ping / getLicense 无需认证")
    class PublicEndpoints {

        @Test
        @DisplayName("ping 无需认证")
        void pingNoAuth() throws Exception {
            when(request.getRequestURI()).thenReturn("/rest/ping");

            SubsonicAuthFilter filter = new SubsonicAuthFilter(passwordLookup);
            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);
        }

        @Test
        @DisplayName("ping.view 无需认证")
        void pingView() throws Exception {
            when(request.getRequestURI()).thenReturn("/rest/ping.view");

            SubsonicAuthFilter filter = new SubsonicAuthFilter(passwordLookup);
            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);
        }

        @Test
        @DisplayName("getLicense 无需认证")
        void getLicenseNoAuth() throws Exception {
            when(request.getRequestURI()).thenReturn("/rest/getLicense");

            SubsonicAuthFilter filter = new SubsonicAuthFilter(passwordLookup);
            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);
        }

        @Test
        @DisplayName("getLicense.view 无需认证")
        void getLicenseView() throws Exception {
            when(request.getRequestURI()).thenReturn("/rest/getLicense.view");

            SubsonicAuthFilter filter = new SubsonicAuthFilter(passwordLookup);
            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);
        }

    }


    private static String computeToken(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(md.digest((password + salt).getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}

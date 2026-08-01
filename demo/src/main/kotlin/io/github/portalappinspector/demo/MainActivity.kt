package io.github.portalappinspector.demo

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

private const val DemoLogTag = "PortalDemo"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(DemoLogTag, "MainActivity created")
        println("PortalDemo print: activity created")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4896)
        }
        setContent {
            DemoApp()
        }
    }
}

@Composable
private fun DemoApp() {
    var postsState by remember { mutableStateOf<ApiState<List<Post>>>(ApiState.Loading) }
    var selectedPost by remember { mutableStateOf<Post?>(null) }
    var commentsState by remember { mutableStateOf<ApiState<List<PostComment>>?>(null) }
    var reloadPostsKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadPostsKey) {
        Log.d(DemoLogTag, "Loading posts, reloadKey=$reloadPostsKey")
        postsState = ApiState.Loading
        postsState = runCatching { fetchPosts() }
            .fold(
                onSuccess = {
                    Log.i(DemoLogTag, "Loaded ${it.size} posts")
                    ApiState.Success(it)
                },
                onFailure = {
                    Log.e(DemoLogTag, "Failed to load posts", it)
                    ApiState.Error(it.message ?: "Failed to load posts")
                },
            )
    }

    LaunchedEffect(selectedPost?.id) {
        val post = selectedPost ?: return@LaunchedEffect
        Log.d(DemoLogTag, "Loading comments for post ${post.id}")
        commentsState = ApiState.Loading
        commentsState = runCatching { fetchComments(post.id) }
            .fold(
                onSuccess = {
                    Log.i(DemoLogTag, "Loaded ${it.size} comments for post ${post.id}")
                    ApiState.Success(it)
                },
                onFailure = {
                    Log.w(DemoLogTag, "Failed to load comments for post ${post.id}", it)
                    ApiState.Error(it.message ?: "Failed to load comments")
                },
            )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101113))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(onReload = { reloadPostsKey++ })
        PostsContent(
            postsState = postsState,
            selectedPost = selectedPost,
            commentsState = commentsState,
            onSelectPost = {
                Log.d(DemoLogTag, "Selected post ${it.id}: ${it.title}")
                System.err.println("PortalDemo stderr: selected post ${it.id}")
                selectedPost = it
            },
        )
    }
}

@Composable
private fun Header(onReload: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Portal App Inspector Demo",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Loads posts from JSONPlaceholder. Tap a post to request its comments.",
                color = Color(0xFFB6BECF),
                fontSize = 15.sp,
                lineHeight = 22.sp,
            )
        }
        DemoButton(text = "Reload", onClick = onReload)
    }
}

@Composable
private fun PostsContent(
    postsState: ApiState<List<Post>>,
    selectedPost: Post?,
    commentsState: ApiState<List<PostComment>>?,
    onSelectPost: (Post) -> Unit,
) {
    when (postsState) {
        ApiState.Loading -> StatusText("GET /posts is loading...")
        is ApiState.Error -> StatusText("GET /posts failed: ${postsState.message}")
        is ApiState.Success -> LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(postsState.value, key = { it.id }) { post ->
                PostCard(
                    post = post,
                    selected = post.id == selectedPost?.id,
                    commentsState = if (post.id == selectedPost?.id) commentsState else null,
                    onClick = { onSelectPost(post) },
                )
            }
        }
    }
}

@Composable
private fun PostCard(
    post: Post,
    selected: Boolean,
    commentsState: ApiState<List<PostComment>>?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) Color(0xFF24354E) else Color(0xFF181B20), RoundedCornerShape(8.dp))
            .border(1.dp, if (selected) Color(0xFF7DB1FF) else Color(0xFF303743), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "GET /posts/${post.id}/comments",
            color = Color(0xFF83C5BE),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = post.title,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 22.sp,
        )
        Text(
            text = post.body,
            color = Color(0xFFD2D8E5),
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        if (!selected) {
            DemoButton(text = "Open comments", onClick = onClick)
        }
        if (selected) {
            CommentsContent(commentsState)
        }
    }
}

@Composable
private fun CommentsContent(commentsState: ApiState<List<PostComment>>?) {
    when (commentsState) {
        null, ApiState.Loading -> StatusText("GET comments is loading...")
        is ApiState.Error -> StatusText("GET comments failed: ${commentsState.message}")
        is ApiState.Success -> Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Comments",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            commentsState.value.take(3).forEach { comment ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = comment.email,
                        color = Color(0xFF7DB1FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = comment.body,
                        color = Color(0xFFD2D8E5),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusText(message: String) {
    Text(
        text = message,
        color = Color(0xFFB6BECF),
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )
}

@Composable
private fun DemoButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(Color(0xFF2B3340), RoundedCornerShape(7.dp))
            .border(1.dp, Color(0xFF3D4858), RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    fontWeight: FontWeight? = null,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            lineHeight = lineHeight,
        ),
        maxLines = maxLines,
        overflow = overflow,
    )
}

private sealed interface ApiState<out T> {
    data object Loading : ApiState<Nothing>
    data class Success<T>(val value: T) : ApiState<T>
    data class Error(val message: String) : ApiState<Nothing>
}

private data class Post(
    val id: Int,
    val title: String,
    val body: String,
)

private data class PostComment(
    val email: String,
    val body: String,
)

private suspend fun fetchPosts(): List<Post> {
    Log.v(DemoLogTag, "GET /posts")
    return jsonPlaceholderApi.getPosts()
}

private suspend fun fetchComments(postId: Int): List<PostComment> {
    Log.v(DemoLogTag, "GET /posts/$postId/comments")
    return jsonPlaceholderApi.getComments(postId)
}

private val jsonPlaceholderApi: JsonPlaceholderApi by lazy {
    Retrofit.Builder()
        .baseUrl("https://jsonplaceholder.typicode.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .client(
            OkHttpClient()
                .newBuilder()
                .build()
        )
        .build()
        .create(JsonPlaceholderApi::class.java)
}

private interface JsonPlaceholderApi {
    @GET("posts")
    suspend fun getPosts(): List<Post>

    @GET("posts/{postId}/comments")
    suspend fun getComments(@Path("postId") postId: Int): List<PostComment>
}

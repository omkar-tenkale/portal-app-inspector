package io.github.portalappinspector.demo

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.openflocon.flocon.okhttp.FloconOkhttpInterceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        postsState = ApiState.Loading
        postsState = runCatching { fetchPosts() }
            .fold(
                onSuccess = { ApiState.Success(it) },
                onFailure = { ApiState.Error(it.message ?: "Failed to load posts") },
            )
    }

    LaunchedEffect(selectedPost?.id) {
        val post = selectedPost ?: return@LaunchedEffect
        commentsState = ApiState.Loading
        commentsState = runCatching { fetchComments(post.id) }
            .fold(
                onSuccess = { ApiState.Success(it) },
                onFailure = { ApiState.Error(it.message ?: "Failed to load comments") },
            )
    }

    MaterialTheme {
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
                onSelectPost = { selectedPost = it },
            )
        }
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
        Button(onClick = onReload) {
            Text("Reload")
        }
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFF24354E) else Color(0xFF181B20),
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) Color(0xFF7DB1FF) else Color(0xFF303743),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
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
                TextButton(onClick = onClick) {
                    Text("Open comments")
                }
            }
            if (selected) {
                CommentsContent(commentsState)
            }
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

private suspend fun fetchPosts(): List<Post> = jsonPlaceholderApi.getPosts()

private suspend fun fetchComments(postId: Int): List<PostComment> =
    jsonPlaceholderApi.getComments(postId)

private val jsonPlaceholderApi: JsonPlaceholderApi by lazy {
    Retrofit.Builder()
        .baseUrl("https://jsonplaceholder.typicode.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .client(
            OkHttpClient()
                .newBuilder()
                .addInterceptor(FloconOkhttpInterceptor())
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

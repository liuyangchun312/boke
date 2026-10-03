package com.example.blog.seed;

import com.example.blog.auth.service.UserService;
import com.example.blog.post.dto.PostRequest;
import com.example.blog.post.model.Post;
import com.example.blog.post.model.PostStatus;
import com.example.blog.post.repository.PostRepository;
import com.example.blog.post.service.PostService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Component
@Profile("dev")
public class DataSeeder implements CommandLineRunner {
    private static final String RELATED_ARTICLES_SEED = "related-articles-2026-10-v1";
    private final UserService userService;
    private final PostService postService;
    private final PostRepository postRepository;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;

    public DataSeeder(UserService userService, PostService postService, PostRepository postRepository,
                      JdbcTemplate jdbc, ObjectMapper objectMapper, PlatformTransactionManager transactionManager) {
        this.userService = userService;
        this.postService = postService;
        this.postRepository = postRepository;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(String... args) throws IOException {
        userService.createIfAbsent("admin", "admin123", "刘杨春", "ADMIN");
        if (postService.search(1, 1, null, null, null, false).total() == 0) {
            seedOriginalPosts();
        }
        seedRelatedArticles();
    }

    private void seedOriginalPosts() {
        seedPost(
                "白羽之下，武山脚下的乌鸡",
                "taihe-black-bone-chicken-wushan",
                "乡味物产",
                List.of("泰和乌鸡", "武山"),
                "泰和乌鸡常被称作武山鸡。白色丝羽与乌皮、乌肉、乌骨相映成趣，也把一段地方物产史留在了武山脚下。",
                "/covers/taihe-uhji.svg",
                "# 白羽乌骨\n\n第一次听到泰和乌鸡这个名字时，我也曾以为它应该通体乌黑。真正见到它，反而会先被白色丝状羽毛吸引：羽毛轻软，脚上有毛，乌皮、乌肉和乌骨藏在外表之下，形成一种安静而特别的反差。\n\n泰和乌鸡又常被称作武山鸡。地方经验里，人们会用丛冠、缨头、绿耳、胡须、丝毛、毛脚、五爪，以及乌皮、乌肉、乌骨来辨认它。对我来说，这些名目不是标签，而是乡土生活一点点留下来的观察方法。\n\n一只鸡从山脚的养殖场走进市场，也把武山的地名、气候和饮食记忆带到了更远的地方。说起这份乡味，最重要的不是夸张的功效，而是尊重它的产地与日常。好的地方物产，应该先让人记住它从哪里来，再慢慢品出它的滋味。");
        seedPost(
                "赣江把村庄围成一座岛：蜀口的一天",
                "a-day-on-shukou-eco-island",
                "江河田园",
                List.of("赣江", "蜀口生态岛"),
                "在马市镇的赣江江心洲上，村落、农田、道路和水岸共同组成蜀口生态岛的生活景观。",
                "/covers/taihe-shukou.svg",
                "# 赣江上的村庄\n\n清晨的蜀口生态岛，先听见的是水声。赣江从岛屿两侧缓缓展开，村舍、田埂和通向江岸的小路，在雾气散开之后逐一显露出来。\n\n这里是泰和县马市镇赣江河道中的有人居住江心洲。岛上的生活并不急着成为风景：有人照料田地，有人沿着道路进出，有人把一天的家务安排在江风里。正是这些普通的动作，让“生态岛”不只是一个地名，也成为可以触摸的生活空间。\n\n到了傍晚，水面把天光拉得很长。站在江堤边看村庄亮起灯，会发现所谓田园并非与现代生活隔绝，而是生产、居住和自然水岸共同组成的秩序。蜀口的好看，正在于它仍然有人在这里认真过日子。");
        seedPost(
                "一座仍在工作的古陂：槎滩陂",
                "chatanbei-ancient-irrigation",
                "水利遗产",
                List.of("槎滩陂", "赣江"),
                "槎滩陂不是停在展柜里的古迹。它与碉石陂和渠道共同构成灌溉系统，古老的水利智慧至今仍参与着禾市一带的田野生活。",
                "/covers/taihe-chatanbei.svg",
                "# 一座仍在工作的古陂\n\n在泰和县禾市镇牛吼江水系附近，槎滩陂把“古老”写成了一个仍然进行时的动词。地方资料通常记载，它始建于南唐升元元年（937），由周矩主持修建；后来又与碉石陂、渠道等设施连成一套灌溉系统。\n\n站在陂旁看水流，最容易想到的不是年代数字，而是古人如何顺着地势安排水路，让一方田地在旱涝之间多一份从容。工程留下来的意义，也不只在石头和堤岸，它还在今天的田野里继续发挥作用。\n\n槎滩陂于2013年列入第七批全国重点文物保护单位，2016年入选国际灌排委员会（ICID）世界灌溉工程遗产名录。对一处水利遗产来说，最动人的保护方式，正是让人理解它、珍惜它，并看见它与当地生活仍有联系。");
        seedPost(
                "澄江镇，从早市到晚风",
                "a-day-in-chengjiang-town",
                "小城日常",
                List.of("澄江镇"),
                "从一顿早饭、一段江堤路，到放学后的街灯，澄江镇的日常不喧哗，却把泰和县城的节奏写得清清楚楚。",
                "/covers/taihe-chengjiang.svg",
                "# 澄江镇，从早市到晚风\n\n小城的一天，往往从早市的声音开始。摊主把新鲜蔬菜摆齐，熟悉的邻居停下来问候两句，街道还没有完全热闹起来，生活已经按自己的节拍启动。\n\n澄江镇是泰和县城生活的重要一隅。白天，人们在街巷、社区和工作之间来回；放学时分，校门口多了等候的身影；傍晚，沿着江堤走一段，风把白天的喧闹慢慢吹散。\n\n小城并不需要时时刻刻制造新鲜感。它的魅力藏在重复而可靠的细节里：一碗熟悉的早餐，一条走过很多次的路，和晚饭后仍亮着的店铺。正是这些微小片段，让“回到澄江”成为一种具体的安心。");
        seedPost(
                "快阁下班以后：从一行诗回到泰和",
                "kuaige-and-huang-tingjian",
                "人文古迹",
                List.of("快阁", "黄庭坚"),
                "快阁始建于唐乾符元年。黄庭坚在吉州太和任县令时登临并写下《登快阁》，一行“澄江”把文学记忆带回了今天的泰和。",
                "/covers/taihe-kuaige.svg",
                "# 快阁晚晴\n\n快阁始建于唐乾符元年（874），初名“慈氏阁”，宋初改称“快阁”。黄庭坚任吉州太和县知县时，曾登临此处并写下《登快阁》：“落木千山天远大，澄江一道月分明。”几句诗把高远的秋意、开阔的江天和人的心绪放在同一幅画面里。\n\n今天再读这首诗，最有意思的并不是把古迹想象成一处凝固的布景，而是去想象当时的县城与江水：木叶落下，远山显出轮廓，澄江在月色里延伸。地名和时代会变化，诗句捕捉到的观看方式却仍然可以被重新打开。\n\n谈快阁，也要把史实和传说分开。快阁的历史沿革、黄庭坚的任职与诗作有明确记载；至于后世附会的故事，则更适合保留为地方记忆。对泰和而言，快阁最珍贵的地方，正在于它让一座县城同时拥有建筑的时间感与文学的回声。");
    }

    private void seedRelatedArticles() throws IOException {
        List<SeedArticle> articles;
        try (var input = new ClassPathResource("seed/related-articles.json").getInputStream()) {
            articles = objectMapper.readValue(input, new TypeReference<>() {});
        }
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS blog_seed_runs (
                    seed_key VARCHAR(100) PRIMARY KEY,
                    applied_at TIMESTAMP(6) NOT NULL
                )
                """);
        transactions.executeWithoutResult(transaction -> {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM blog_seed_runs WHERE seed_key = ?",
                    Integer.class, RELATED_ARTICLES_SEED) > 0) return;
            // The marker and all articles commit together; deleted or renamed articles stay that way on restart.
            try {
                jdbc.update("INSERT INTO blog_seed_runs (seed_key, applied_at) VALUES (?, ?)",
                        RELATED_ARTICLES_SEED, Timestamp.from(Instant.now()));
            } catch (DuplicateKeyException alreadyImported) {
                return;
            }
            for (SeedArticle article : articles) {
                if (postRepository.findBySlug(article.slug()).isPresent()) continue;
                Post post = new Post();
                post.setTitle(article.title());
                post.setSlug(article.slug());
                post.setCategory(article.category());
                post.setTags(article.tags());
                post.setExcerpt(article.excerpt());
                post.setCoverImage(article.coverImage());
                post.setContent(article.content());
                post.setStatus(article.status());
                post.setCreatedAt(article.createdAt());
                post.setUpdatedAt(article.updatedAt());
                post.setPublishedAt(article.publishedAt());
                postRepository.save(post);
            }
        });
    }

    private record SeedArticle(String title, String slug, String category, List<String> tags,
                               String excerpt, String coverImage, String content, PostStatus status,
                               Instant createdAt, Instant updatedAt, Instant publishedAt) {}

    private void seedPost(String title, String slug, String category, List<String> tags,
                          String excerpt, String coverImage, String content) {
        PostRequest request = new PostRequest();
        request.setTitle(title);
        request.setSlug(slug);
        request.setCategory(category);
        request.setTags(tags);
        request.setExcerpt(excerpt);
        request.setCoverImage(coverImage);
        request.setContent(content);
        request.setStatus(PostStatus.PUBLISHED);
        postService.create(request);
    }
}

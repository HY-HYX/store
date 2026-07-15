import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class BrickBreaker extends Application {

    // ----- 常量 -----
    private static final int WIDTH = 480;
    private static final int HEIGHT = 640;
    private static final int PADDLE_WIDTH = 80;
    private static final int PADDLE_HEIGHT = 15;
    private static final int BALL_RADIUS = 8;
    private static final int BRICK_ROWS = 5;
    private static final int BRICK_COLS = 8;
    private static final int BRICK_WIDTH = 50;
    private static final int BRICK_HEIGHT = 20;
    private static final int BRICK_PAD = 5;

    // ----- 游戏状态 -----
    private enum GameState { MENU, RUNNING, PAUSED, GAME_OVER, WIN }

    private GameState state = GameState.MENU;

    // ----- 游戏对象 -----
    private Paddle paddle;
    private Ball ball;
    private List<Brick> bricks;
    private List<PowerUp> powerUps;
    private List<Particle> particles;

    // ----- 属性 -----
    private int score = 0;
    private int lives = 3;
    private int currentLevel = 0;
    private boolean ballLaunched = false;

    // ----- 输入 -----
    private boolean leftPressed = false;
    private boolean rightPressed = false;

    // ----- 道具效果计时器 -----
    private int widePaddleTimer = 0;
    private int slowBallTimer = 0;

    // ----- 关卡数据（硬编码砖块布局）-----
    private int[][][] levelData = {
            { // 第1关
                    {1,1,1,1,1,1,1,1},
                    {1,1,1,1,1,1,1,1},
                    {0,0,0,0,0,0,0,0},
                    {0,0,0,0,0,0,0,0},
                    {0,0,0,0,0,0,0,0}
            },
            { // 第2关
                    {1,0,1,0,1,0,1,0},
                    {0,1,0,1,0,1,0,1},
                    {1,0,1,0,1,0,1,0},
                    {0,0,0,0,0,0,0,0},
                    {0,0,0,0,0,0,0,0}
            },
            { // 第3关
                    {2,2,2,2,2,2,2,2},
                    {2,2,2,2,2,2,2,2},
                    {1,1,1,1,1,1,1,1},
                    {1,1,1,1,1,1,1,1},
                    {3,0,0,3,3,0,0,3}
            }
    };

    // ----- Canvas 与 画笔 -----
    private Canvas canvas;
    private GraphicsContext gc;

    @Override
    public void start(Stage primaryStage) {
        canvas = new Canvas(WIDTH, HEIGHT);
        gc = canvas.getGraphicsContext2D();

        StackPane root = new StackPane(canvas);
        Scene scene = new Scene(root);

        // 键盘事件
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.LEFT) leftPressed = true;
            if (e.getCode() == KeyCode.RIGHT) rightPressed = true;
            if (e.getCode() == KeyCode.SPACE) {
                if (state == GameState.MENU) {
                    startGame();
                } else if (state == GameState.RUNNING && !ballLaunched) {
                    ballLaunched = true;
                    ball.setVelocity(3, -4); // 初始方向
                } else if (state == GameState.GAME_OVER || state == GameState.WIN) {
                    startGame();
                } else if (state == GameState.PAUSED) {
                    state = GameState.RUNNING;
                }
            }
            if (e.getCode() == KeyCode.P && state == GameState.RUNNING) {
                state = GameState.PAUSED;
            }
        });
        scene.setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.LEFT) leftPressed = false;
            if (e.getCode() == KeyCode.RIGHT) rightPressed = false;
        });

        primaryStage.setTitle("Brick Breaker - JavaFX");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();

        // 游戏循环
        new AnimationTimer() {
            private long lastUpdate = 0;
            @Override
            public void handle(long now) {
                if (lastUpdate == 0) {
                    lastUpdate = now;
                    return;
                }
                double delta = (now - lastUpdate) / 1_000_000_000.0; // 秒
                if (delta > 0.1) delta = 0.1; // 防止卡顿后跳帧
                lastUpdate = now;

                update(delta);
                render();
            }
        }.start();
    }

    // ----- 游戏初始化与重置 -----
    private void startGame() {
        state = GameState.RUNNING;
        score = 0;
        lives = 3;
        currentLevel = 0;
        ballLaunched = false;
        widePaddleTimer = 0;
        slowBallTimer = 0;

        paddle = new Paddle(WIDTH/2 - PADDLE_WIDTH/2, HEIGHT - 40, PADDLE_WIDTH);
        ball = new Ball(WIDTH/2, paddle.y - BALL_RADIUS - 2, BALL_RADIUS);
        powerUps = new ArrayList<>();
        particles = new ArrayList<>();
        loadLevel(currentLevel);
    }

    private void loadLevel(int level) {
        bricks = new ArrayList<>();
        int[][] layout = levelData[level % levelData.length];
        for (int r = 0; r < layout.length; r++) {
            for (int c = 0; c < layout[r].length; c++) {
                int type = layout[r][c];
                if (type > 0) {
                    double bx = c * (BRICK_WIDTH + BRICK_PAD) + 35;
                    double by = r * (BRICK_HEIGHT + BRICK_PAD) + 80;
                    bricks.add(new Brick(bx, by, BRICK_WIDTH, BRICK_HEIGHT, type));
                }
            }
        }
    }

    // ----- 更新逻辑 -----
    private void update(double dt) {
        if (state != GameState.RUNNING) return;

        // 道具计时器
        if (widePaddleTimer > 0) {
            widePaddleTimer--;
            if (widePaddleTimer == 0) {
                paddle.width = PADDLE_WIDTH; // 恢复原始宽度
            }
        }
        if (slowBallTimer > 0) {
            slowBallTimer--;
            if (slowBallTimer == 0) {
                // 恢复球速到正常（不改变方向，只把大小恢复到正常）
                double curSpeed = Math.sqrt(ball.dx*ball.dx + ball.dy*ball.dy);
                if (curSpeed > 0) {
                    double normalSpeed = 4.0;
                    ball.dx = ball.dx / curSpeed * normalSpeed;
                    ball.dy = ball.dy / curSpeed * normalSpeed;
                }
            }
        }

        // 输入移动挡板
        double paddleSpeed = 400;
        if (leftPressed) paddle.x -= paddleSpeed * dt;
        if (rightPressed) paddle.x += paddleSpeed * dt;
        // 边界限制
        if (paddle.x < 0) paddle.x = 0;
        if (paddle.x + paddle.width > WIDTH) paddle.x = WIDTH - paddle.width;

        // 球跟随挡板（未发射时）
        if (!ballLaunched) {
            ball.x = paddle.x + paddle.width/2;
            ball.y = paddle.y - ball.radius - 1;
            return;
        }

        // 移动球
        ball.x += ball.dx;
        ball.y += ball.dy;

        // 墙壁碰撞
        if (ball.x - ball.radius <= 0 || ball.x + ball.radius >= WIDTH) {
            ball.dx = -ball.dx;
            ball.x = ball.x - ball.radius <= 0 ? ball.radius : WIDTH - ball.radius;
        }
        if (ball.y - ball.radius <= 0) {
            ball.dy = -ball.dy;
            ball.y = ball.radius;
        }

        // 底部（掉球）
        if (ball.y + ball.radius > HEIGHT) {
            lives--;
            if (lives <= 0) {
                state = GameState.GAME_OVER;
            } else {
                // 重置球和挡板
                ballLaunched = false;
                paddle.x = WIDTH/2 - paddle.width/2;
                ball.x = paddle.x + paddle.width/2;
                ball.y = paddle.y - ball.radius - 2;
                // 重置道具效果
                widePaddleTimer = 0;
                slowBallTimer = 0;
                paddle.width = PADDLE_WIDTH;
            }
            return;
        }

        // 挡板碰撞
        if (ball.y + ball.radius >= paddle.y &&
                ball.y - ball.radius <= paddle.y + PADDLE_HEIGHT &&
                ball.x + ball.radius >= paddle.x &&
                ball.x - ball.radius <= paddle.x + paddle.width) {

            // 反弹角度根据碰撞位置变化
            double hitPos = (ball.x - paddle.x) / paddle.width;
            double angle = (hitPos - 0.5) * Math.PI * 0.7; // 最大偏角
            double speed = Math.sqrt(ball.dx*ball.dx + ball.dy*ball.dy);
            ball.dx = Math.sin(angle) * speed;
            ball.dy = -Math.cos(angle) * speed;
            ball.y = paddle.y - ball.radius - 1;
        }

        // 砖块碰撞
        Iterator<Brick> brickIter = bricks.iterator();
        while (brickIter.hasNext()) {
            Brick brick = brickIter.next();
            if (checkCollision(ball, brick)) {
                // 砖块受伤
                brick.hp--;
                score += 10;
                if (brick.hp <= 0) {
                    brickIter.remove();
                    score += 20;
                    // 生成粒子特效
                    for (int i = 0; i < 8; i++) {
                        particles.add(new Particle(brick.x + brick.width/2, brick.y + brick.height/2,
                                Color.hsb(brick.type * 60, 1, 1)));
                    }
                    // 道具掉落概率15%
                    if (Math.random() < 0.15) {
                        powerUps.add(new PowerUp(brick.x + brick.width/2, brick.y + brick.height,
                                PowerUp.randomType()));
                    }
                }
                // 反弹（简化处理，只反转Y）
                ball.dy = -ball.dy;
                break; // 一次只能碰撞一个砖块
            }
        }

        // 道具更新与收集
        for (PowerUp pu : powerUps) {
            pu.y += 150 * dt;
        }
        Iterator<PowerUp> puIter = powerUps.iterator();
        while (puIter.hasNext()) {
            PowerUp pu = puIter.next();
            if (pu.y > HEIGHT) {
                puIter.remove();
                continue;
            }
            // 与挡板碰撞
            if (pu.y + pu.size >= paddle.y &&
                    pu.y <= paddle.y + PADDLE_HEIGHT &&
                    pu.x + pu.size >= paddle.x &&
                    pu.x <= paddle.x + paddle.width) {
                applyPowerUp(pu.type);
                puIter.remove();
            }
        }

        // 粒子更新
        for (Particle p : particles) {
            p.life -= dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.vy += 200 * dt; // 重力
        }
        particles.removeIf(p -> p.life <= 0);

        // 检查是否所有砖块消除
        if (bricks.isEmpty()) {
            currentLevel++;
            if (currentLevel < levelData.length) {
                loadLevel(currentLevel);
                ballLaunched = false;
                paddle.x = WIDTH/2 - paddle.width/2;
                ball.x = paddle.x + paddle.width/2;
                ball.y = paddle.y - ball.radius - 2;
                widePaddleTimer = 0;
                slowBallTimer = 0;
                paddle.width = PADDLE_WIDTH;
            } else {
                state = GameState.WIN;
            }
        }
    }

    // AABB 碰撞检测（球与砖块）
    private boolean checkCollision(Ball ball, Brick brick) {
        double closestX = clamp(ball.x, brick.x, brick.x + brick.width);
        double closestY = clamp(ball.y, brick.y, brick.y + brick.height);
        double dx = ball.x - closestX;
        double dy = ball.y - closestY;
        return (dx*dx + dy*dy) < (ball.radius * ball.radius);
    }

    private double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    // 应用道具效果
    private void applyPowerUp(PowerUp.Type type) {
        switch (type) {
            case WIDE_PADDLE:
                paddle.width = PADDLE_WIDTH * 1.5;
                widePaddleTimer = 400; // 持续时间（帧）
                break;
            case SLOW_BALL:
                double curSpeed = Math.sqrt(ball.dx*ball.dx + ball.dy*ball.dy);
                if (curSpeed > 0) {
                    double slowSpeed = 2.5;
                    ball.dx = ball.dx / curSpeed * slowSpeed;
                    ball.dy = ball.dy / curSpeed * slowSpeed;
                }
                slowBallTimer = 400;
                break;
            case EXTRA_LIFE:
                lives++;
                break;
        }
    }

    // ----- 渲染 -----
    private void render() {
        // 背景
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, WIDTH, HEIGHT);

        switch (state) {
            case MENU:
                drawCenteredText("BRICK BREAKER", 200, 30);
                drawCenteredText("Press SPACE to start", 300, 20);
                break;
            case RUNNING:
            case PAUSED:
                // 挡板
                gc.setFill(Color.CYAN);
                gc.fillRect(paddle.x, paddle.y, paddle.width, PADDLE_HEIGHT);
                // 球
                gc.setFill(Color.WHITE);
                gc.fillOval(ball.x - ball.radius, ball.y - ball.radius, ball.radius*2, ball.radius*2);
                // 砖块
                for (Brick b : bricks) {
                    Color c;
                    switch (b.type) {
                        case 2: c = Color.ORANGE; break;
                        case 3: c = Color.RED; break;
                        default: c = Color.LIME; break;
                    }
                    if (b.hp == 1) c = c.darker();
                    gc.setFill(c);
                    gc.fillRect(b.x, b.y, b.width, b.height);
                    gc.setStroke(Color.BLACK);
                    gc.strokeRect(b.x, b.y, b.width, b.height);
                }
                // 道具
                for (PowerUp pu : powerUps) {
                    switch (pu.type) {
                        case WIDE_PADDLE:
                            gc.setFill(Color.CYAN);
                            gc.fillText("[W]", pu.x - 8, pu.y - 5);
                            break;
                        case SLOW_BALL:
                            gc.setFill(Color.BLUE);
                            gc.fillText("[S]", pu.x - 8, pu.y - 5);
                            break;
                        case EXTRA_LIFE:
                            gc.setFill(Color.RED);
                            gc.fillText("[♥]", pu.x - 8, pu.y - 5);
                            break;
                    }
                }
                // 粒子
                for (Particle p : particles) {
                    gc.setFill(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), p.life / 1.0));
                    gc.fillOval(p.x - 2, p.y - 2, 4, 4);
                }
                // HUD
                gc.setFill(Color.WHITE);
                gc.setFont(new Font(16));
                gc.fillText("Score: " + score, 10, 20);
                gc.fillText("Lives: " + lives, 10, 40);
                gc.fillText("Level: " + (currentLevel+1), 10, 60);

                if (state == GameState.PAUSED) {
                    gc.setFill(new Color(0, 0, 0, 0.7));
                    gc.fillRect(0, 0, WIDTH, HEIGHT);
                    drawCenteredText("PAUSED", 300, 25);
                    drawCenteredText("Press P to resume", 350, 18);
                }

                if (!ballLaunched && state == GameState.RUNNING) {
                    drawCenteredText("Press SPACE to launch", 500, 18);
                }
                break;
            case GAME_OVER:
                drawCenteredText("GAME OVER", 280, 30);
                drawCenteredText("Score: " + score, 330, 20);
                drawCenteredText("Press SPACE to restart", 400, 18);
                break;
            case WIN:
                drawCenteredText("YOU WIN!", 280, 30);
                drawCenteredText("Final Score: " + score, 330, 20);
                drawCenteredText("Press SPACE to play again", 400, 18);
                break;
        }
    }

    private void drawCenteredText(String text, double y, int size) {
        gc.setFill(Color.WHITE);
        gc.setFont(new Font(size));
        double textWidth = gc.getFont().getSize() * text.length() * 0.5; // 估算
        gc.fillText(text, WIDTH/2 - textWidth/2, y);
    }

    // ----- 内部类（游戏对象）-----
    static class Paddle {
        double x, y, width;
        Paddle(double x, double y, double width) {
            this.x = x; this.y = y; this.width = width;
        }
    }

    static class Ball {
        double x, y, radius, dx, dy;
        Ball(double x, double y, double radius) {
            this.x = x; this.y = y; this.radius = radius;
            this.dx = 0; this.dy = 0;
        }
        void setVelocity(double dx, double dy) {
            this.dx = dx; this.dy = dy;
        }
    }

    static class Brick {
        double x, y, width, height;
        int type; // 1: 普通, 2: 坚固, 3: 极坚固
        int hp;
        Brick(double x, double y, double w, double h, int type) {
            this.x = x; this.y = y; this.width = w; this.height = h;
            this.type = type;
            this.hp = type; // 血量等于类型值
        }
    }

    static class PowerUp {
        double x, y;
        static final int size = 12;
        Type type;
        enum Type { WIDE_PADDLE, SLOW_BALL, EXTRA_LIFE }
        static Type randomType() {
            int r = new Random().nextInt(3);
            return Type.values()[r];
        }
        PowerUp(double x, double y, Type type) {
            this.x = x - size/2;
            this.y = y;
            this.type = type;
        }
    }

    static class Particle {
        double x, y, vx, vy, life;
        Color color;
        Particle(double x, double y, Color color) {
            this.x = x; this.y = y;
            this.color = color;
            Random rnd = new Random();
            vx = (rnd.nextDouble() - 0.5) * 100;
            vy = (rnd.nextDouble() - 0.5) * 100 - 50;
            life = 1.0;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}


<div class="container">
    <div class="con-form">
    <h2>Login</h2>
        <hr>
            <form class="form" action="/" method="post">
                <div class="form-group">
                        <label for="email">Email Address</label>
                        <input type="text" class="form-control" name="email" id="email" value="<?= set_value('email') ?>"
                        placeholder="Please  your email address...">
                </div>
                <div class="form-group">
                        <label for="password">Password</label>
                        <input type="password" class="form-control" name="password" id="password" value=""
                        placeholder="Please type your password...">
                </div>
                <div class="row">
                    <div class="col">
                        <button type="submit" class="btn">Login</button>
                    </div>
                    <div class="col">
                            <p>Don't have an account yet?
                            <a href="/register">Register here</a></p>
                    </div>
                </div>
            </form>
    </div>
</div>
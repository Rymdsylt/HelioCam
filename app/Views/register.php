<div class="container">
    <div class="con-form">
    <h2>Sign Up</h2>
        <hr>
            <form class="form" action="/" method="post">
                <div class="form-group">
                        <label for="email">Full Name</label>
                        <input type="text" class="form-control" name="fname" id="fname" value=""
                        placeholder="Please type your full name...">
                </div>
                <div class="form-group">
                        <label for="email">User Name</label>
                        <input type="text" class="form-control" name="uname" id="uname" value=""
                        placeholder="Please type your user name...">
                </div>
                <div class="form-group">
                        <label for="email">Contact #</label>
                        <input type="text" class="form-control" name="contact" id="contact" value=""
                        placeholder="Please type your contact #...">
                </div>
                <div class="form-group">
                        <label for="email">Email Address</label>
                        <input type="text" class="form-control" name="email" id="email" value="<?= set_value('email') ?>"
                        placeholder="Please type your email address...">
                </div>
                <div class="form-group">
                        <label for="password">Password</label>
                        <input type="password" class="form-control" name="password" id="password" value=""
                        placeholder="Please type your password...">
                </div>
                <div class="form-group">
                        <label for="con_pass">Confirm Password</label>
                        <input type="password" class="form-control" name="con_pass" id="con_pass" value=""
                        placeholder="Please type your password...">
                </div>
                <div class="row">
                    <div class="col">
                        <button type="submit" class="btn">Register</button>
                    </div>
                    <div class="col">
                            <p>Already have an account?
                            <a href="/">Login here</a></p>
                    </div>
                </div>
            </form>
    </div>
</div>
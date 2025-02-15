<?php

namespace App\Controllers;

class Users extends BaseController
{
    public function index()
    {
        $data = [];
        helper(['form']);

        echo view('templates/header', $data);
        echo view('login');
        echo view('templates/footer');
        
    }
   public function register(){
    $data = [];
    helper(['form']);

    echo view('templates/header', $data);
    echo view('register');
    echo view('templates/header');
   }

}

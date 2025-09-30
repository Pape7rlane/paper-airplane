package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"mime/multipart"
	"net/http"
	"os"
	"path/filepath"
		"github.com/joho/godotenv"
)

const (
	repoOwner = "Firefly-Shelter"
	repoName  = "FireflyGo_Android"
	giteaURL  = "https://git.kain.io.vn"
)

type ReleaseInput struct {
	TagName    string `json:"tag_name"`
	Name       string `json:"name"`
	Body       string `json:"body"`
	Draft      bool   `json:"draft"`
	Prerelease bool   `json:"prerelease"`
}

type ReleaseResponse struct {
	ID      int    `json:"id"`
	HTMLURL string `json:"html_url"`
	URL     string `json:"url"`
}

func readFile(path string) string {
	data, err := os.ReadFile(path)
	if err != nil {
		panic(fmt.Sprintf("Failed to read %s: %v", path, err))
	}
	return string(data)
}

func main() {
    err := godotenv.Load("script/.env")
	if err != nil {
		fmt.Println("Error loading .env file")
	}

	token := os.Getenv("TOKEN")
	if token == "" {
		fmt.Println("TOKEN not found in .env")
	}


	releaseJSON := readFile("script/release.json")
	var meta map[string]string
	if err := json.Unmarshal([]byte(releaseJSON), &meta); err != nil {
		panic("Invalid release.json")
	}
	tag := meta["tag"]
	title := meta["title"]
	body := readFile("script/README_Note.md")

	// Step 1: Create release
	releaseInput := ReleaseInput{
		TagName:    tag,
		Name:       title,
		Body:       body,
		Draft:      false,
		Prerelease: false,
	}
	payload, _ := json.Marshal(releaseInput)

	req, _ := http.NewRequest("POST",
		fmt.Sprintf("%s/api/v1/repos/%s/%s/releases", giteaURL, repoOwner, repoName),
		bytes.NewReader(payload),
	)
	req.Header.Set("Authorization", "token "+token)
	req.Header.Set("Content-Type", "application/json")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		panic(err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusCreated {
		bodyBytes, _ := io.ReadAll(resp.Body)
		panic(fmt.Sprintf("Failed to create release: %s", bodyBytes))
	}

	var releaseResp ReleaseResponse
	if err := json.NewDecoder(resp.Body).Decode(&releaseResp); err != nil {
		panic("Failed to decode release response")
	}

	fmt.Printf("Release created:\n- ID: %d\n- HTML: %s\n", releaseResp.ID, releaseResp.HTMLURL)

	uploadURL := releaseResp.URL
	if uploadURL == "" {
		panic("url missing in release response")
	}

	files, err := os.ReadDir("app/release")
	if err != nil {
		panic("Cannot read prebuild folder")
	}

	for _, file := range files {
		if filepath.Ext(file.Name()) != ".apk" {
			continue
		}
		uploadPath := filepath.Join("app/release", file.Name())
		fmt.Println("Uploading:", uploadPath)

		buf := new(bytes.Buffer)
		writer := multipart.NewWriter(buf)
		part, err := writer.CreateFormFile("attachment", filepath.Base(uploadPath))
		if err != nil {
			fmt.Printf("Failed to create form: %v\n", err)
			continue
		}

		src, err := os.Open(uploadPath)
		if err != nil {
			fmt.Printf("Cannot open file %s: %v\n", file.Name(), err)
			continue
		}
		io.Copy(part, src)
		src.Close()
		writer.Close()

		req, err := http.NewRequest("POST", uploadURL+"/assets", buf)
		if err != nil {
			fmt.Printf("NewRequest error: %v\n", err)
			continue
		}
		req.Header.Set("Authorization", "token "+token)
		req.Header.Set("Content-Type", writer.FormDataContentType())

		resp, err := http.DefaultClient.Do(req)
		if err != nil {
			fmt.Printf("Upload failed: %v\n", err)
			continue
		}
		if resp.StatusCode != http.StatusCreated {
			bodyBytes, _ := io.ReadAll(resp.Body)
			fmt.Printf("Upload failed: %s\n", bodyBytes)
		} else {
			fmt.Println("Uploaded:", file.Name())
		}
		resp.Body.Close()
	}
}

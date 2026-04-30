#### 设置pip镜像

```python
# 使用pip命令设置镜像源
pip config set global.index-url https://mirrors.aliyun.com/pypi/simple/
pip config set install.trusted-host mirrors.aliyun.com

# 查看当前pip配置
pip config list

# 或查看源地址
pip config get global.index-url
```

#### py脚本设置代理

```shell
# python 脚本设置系统代理
import os

os.environ["HTTP_PROXY"] = "http://127.0.0.1:7890"
os.environ["HTTPS_PROXY"] = "http://127.0.0.1:7890"
```

#### 设置链接link

```shell
# 创建链接方式
mklink /j "C:\Users\RanGwaz\AppData" "D:\AppData"
```

#### 杀死占用某个端口的进程

```shell
netstat -ano | findstr :8080

# 你会看到类似 TCP 0.0.0.0:8080 0.0.0.0:0 LISTENING 12345 的结果，其中最后一列的 12345 就是占用这个端口的 进程ID (PID)

# 结束进程：输入并运行下面的命令，把 12345 替换成你上一步查到的PID就行
taskkill /PID 12345 /F
```

























#### 启动所有服务的命令

```shell
# 中间件启动
cd H:\桌面\一坨屎\infra\docker

docker compose up -d mysql redis minio minio-init etcd milvus attu kafka kafka-init elasticsearch kibana feature-engineering-scheduler embedding-extract-scheduler

docker compose up -d mysql redis minio minio-init etcd milvus attu kafka kafka-init feature-engineering-scheduler embedding-extract-scheduler

docker compose up -d mysql redis minio minio-init etcd milvus attu feature-engineering-scheduler embedding-extract-scheduler

4、
cd infra\docker
docker compose up -d mysql redis minio minio-init etcd milvus attu kafka kafka-init feature-engineering-scheduler embedding-extract-scheduler i2i-neighbor-scheduler user-embedding-scheduler semantic-taxonomy-scheduler


# Python 端服务启动
# 如果你的 PowerShell 激活经常失败，先加载 conda hook
& "G:\Anaconda\shell\condabin\conda-hook.ps1"
conda activate D:\conda-envs\deep-rank

cd H:\桌面\一坨屎\infra\docker\deep-rank-service
uvicorn app:app --host 0.0.0.0 --port 18080 --reload

# 前端启动
cd H:\桌面\一坨屎\frontend
npm run dev
```

| 任务            | 频率    | 作用                             |
| :-------------- | :------ | :------------------------------- |
| feature         | 5 分钟  | 刷新用户/帖子特征到 MySQL、Redis |
| embedding       | 30 分钟 | 抽取缺失图片 embedding 到 Milvus |
| i2i             | 60 分钟 | 刷新协同过滤相似帖子             |
| user-embeddings | 15 分钟 | 刷新用户向量                     |
| taxonomy        | 24 小时 | 刷新语义分类和 tag vocabulary    |
